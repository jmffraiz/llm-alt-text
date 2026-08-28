package biz.netcentric.aialttext.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.aialttext.exception.AltTextGenerationException;
import biz.netcentric.aialttext.service.AltTextGenerationService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/** Implementation of AltTextGenerationService using Azure OpenAI GPT Vision model with resilience4j circuit breaker and prompt caching. */
@Component(service = AltTextGenerationService.class, immediate = true)
@Designate(ocd = AzureOpenAIConfiguration.class)
public class AltTextGenerationServiceImpl implements AltTextGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(AltTextGenerationServiceImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CONTENT_TYPE = "application/json";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String SYSTEM_USER_NAME = "aialttext-service";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private String apiKey;
    private String endpoint;
    private String deploymentName;
    private int maxTokens;
    private Double temperature;
    private String promptsBasePath;
    private String defaultPromptType;
    private long promptCacheTtlMillis;
    private String fallbackPromptEn;

    private CircuitBreaker circuitBreaker;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connectionManager;

    // Prompt cache: key = "{promptType}-{language}", value = CachedPrompt
    private final Map<String, CachedPrompt> promptCache = new ConcurrentHashMap<>();

    /** Inner class to hold cached prompt with expiry time. */
    private static class CachedPrompt {
        final String content;
        final long expiryTimeMillis;

        CachedPrompt(String content, long expiryTimeMillis) {
            this.content = content;
            this.expiryTimeMillis = expiryTimeMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTimeMillis;
        }
    }

    @Activate
    protected void activate(AzureOpenAIConfiguration config) {
        configure(config);
        LOG.info("Azure OpenAI Alt Text Generation Service activated. Endpoint: {}, Deployment: {}", endpoint, deploymentName);
    }

    @Modified
    protected void modified(AzureOpenAIConfiguration config) {
        close();
        promptCache.clear(); // Clear cache on configuration change
        configure(config);
        LOG.info("Azure OpenAI Alt Text Generation Service configuration updated.");
    }

    @Deactivate
    protected void deactivate() {
        close();
        promptCache.clear();
        LOG.info("Azure OpenAI Alt Text Generation Service deactivated.");
    }

    private void configure(AzureOpenAIConfiguration config) {
        this.apiKey = config.apiKey();
        this.endpoint = config.endpoint();
        this.deploymentName = config.deploymentName();
        this.maxTokens = config.maxTokens();
        if (StringUtils.isBlank(config.temperature())) {
            this.temperature = null;
        } else {
            this.temperature = Double.parseDouble(config.temperature().trim());
        }
        this.promptsBasePath = config.promptsBasePath();
        this.defaultPromptType = config.defaultPromptType();
        this.promptCacheTtlMillis = TimeUnit.SECONDS.toMillis(config.promptCacheTtlSeconds());
        this.fallbackPromptEn = config.fallbackPromptEn();

        // Initialize HTTP connection manager
        connectionManager = new PoolingHttpClientConnectionManager(config.circuitBreakerResetTimeout(), TimeUnit.MILLISECONDS);
        connectionManager.setMaxTotal(10);
        connectionManager.setDefaultMaxPerRoute(5);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.connectionTimeout())
                .setSocketTimeout(config.readTimeout())
                .build();

        // Build HTTP client
        httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Initialize circuit breaker
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(config.circuitBreakerResetTimeout()))
                .slidingWindowSize(config.circuitBreakerFailureThreshold())
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("azureOpenAI");

        // Log circuit breaker state transitions
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> LOG.warn("Circuit breaker state changed: {}", event.getStateTransition()));
    }

    private void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOG.error("Error closing HTTP client", e);
            }
        }
        if (connectionManager != null) {
            connectionManager.close();
        }
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.remove("azureOpenAI");
        }
    }

    @Override
    public String generateAltText(Asset asset, String language, String promptType) throws AltTextGenerationException {
        if (asset == null) {
            throw new AltTextGenerationException("Asset cannot be null");
        }
        if (StringUtils.isBlank(language)) {
            throw new AltTextGenerationException("Language cannot be blank");
        }

        // Get original rendition
        Rendition originalRendition = asset.getRendition("original");
        if (originalRendition == null) {
            throw new AltTextGenerationException("Asset has no original rendition: " + asset.getPath());
        }

        String mimeType = originalRendition.getMimeType();
        if (StringUtils.isBlank(mimeType) || !mimeType.startsWith("image/")) {
            throw new AltTextGenerationException("Asset is not an image: " + asset.getPath() + " (MIME type: " + mimeType + ")");
        }

        try (InputStream inputStream = originalRendition.getStream()) {
            byte[] imageBytes = IOUtils.toByteArray(inputStream);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Load system prompt
            String systemPrompt = loadPrompt(promptType, language);

            // Build API payload
            String jsonPayload = buildApiPayload(systemPrompt, base64Image, mimeType);
            LOG.debug("Built JSON payload for Azure OpenAI API: {}", jsonPayload);

            // Call Azure OpenAI API with circuit breaker
            String altText = circuitBreaker.executeSupplier(() -> {
                try {
                    return callAzureOpenAI(jsonPayload);
                } catch (AltTextGenerationException e) {
                    throw new RuntimeException(e);
                }
            });

            LOG.debug("Generated alt text for asset {}: {}", asset.getPath(), altText);
            return altText;

        } catch (IOException e) {
            throw new AltTextGenerationException("Error reading asset rendition: " + asset.getPath(), e);
        } catch (Exception e) {
            throw new AltTextGenerationException("Error generating alt text for asset: " + asset.getPath(), e);
        }
    }

    /** Load system prompt from DAM or fallback to hardcoded prompt.
     * 
     * @param promptType Prompt type identifier (e.g., "alt-text", "product-image")
     * @param language Language code (e.g., "en", "fr", "de")
     * @return System prompt content
     * @throws AltTextGenerationException */
    private String loadPrompt(String promptType, String language) throws AltTextGenerationException {

        // Use default prompt type if not specified
        if (StringUtils.isBlank(promptType)) {
            LOG.debug("Prompt type is blank; using default prompt type: {}", defaultPromptType);
            promptType = defaultPromptType;
        }

        String cacheKey = buildPromptCacheKey(promptType, language);

        // If prompt type is still blank, use the fallback hardcoded prompt
        if (StringUtils.isBlank(promptType)) {
            LOG.warn("Using fallback hardcoded prompt for language: {}", language);
            String fallbackPrompt = fallbackPromptEn.replace("Output language: English", "Output language: " + language);
            cachePrompt(cacheKey, fallbackPrompt);
            return fallbackPrompt;
        }

        // Check cache
        CachedPrompt cached = promptCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debug("Using cached prompt: {}", cacheKey);
            return cached.content;
        }

        // Load from DAM
        String damPath = promptsBasePath + "/" + cacheKey + ".txt";
        LOG.debug("Attempting to load prompt from DAM: {}", damPath);

        try (ResourceResolver resolver = getSystemResourceResolver()) {
            Resource resource = resolver.getResource(damPath);
            if (resource != null) {
                Asset promptAsset = resource.adaptTo(Asset.class);
                if (promptAsset != null) {
                    Rendition originalRendition = promptAsset.getRendition("original");
                    if (originalRendition != null) {
                        try (InputStream inputStream = originalRendition.getStream()) {
                            String promptContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                            cachePrompt(cacheKey, promptContent);
                            LOG.info("Loaded and cached prompt from DAM: {}", damPath);
                            return promptContent;
                        }
                    }
                }
            }
        } catch (LoginException e) {
            LOG.error("Failed to obtain system resource resolver", e);
        } catch (IOException e) {
            LOG.error("Error reading prompt from DAM: {}", damPath, e);
        }

        LOG.error("Prompt asset not found at DAM path: {}", damPath);
        throw new AltTextGenerationException("Prompt asset not found at DAM path: " + damPath);

    }

    /** Build the prompt cache key from prompt type and language.
     *
     * @param promptType Prompt type identifier
     * @param language Language code
     * @return Cache key in the form "{promptType}-{language}" */
    private String buildPromptCacheKey(String promptType, String language) {
        return promptType + "-" + language;
    }

    /** Cache prompt content under the given key using the configured TTL.
     *
     * @param cacheKey Cache key
     * @param content Prompt content to cache */
    private void cachePrompt(String cacheKey, String content) {
        long expiryTime = System.currentTimeMillis() + promptCacheTtlMillis;
        promptCache.put(cacheKey, new CachedPrompt(content, expiryTime));
    }

    /** Build JSON payload for Azure OpenAI Chat Completions API.
     * 
     * @param systemPrompt System prompt text
     * @param base64Image Base64-encoded image
     * @param mimeType Image MIME type
     * @return JSON payload string */
    String buildApiPayload(String systemPrompt, String base64Image, String mimeType) throws JsonProcessingException {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();

        ArrayNode messages = OBJECT_MAPPER.createArrayNode();

        // System message
        ObjectNode systemMessage = OBJECT_MAPPER.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // User message with image
        ObjectNode userMessage = OBJECT_MAPPER.createObjectNode();
        userMessage.put("role", "user");

        ArrayNode contentArray = OBJECT_MAPPER.createArrayNode();

        // Image content
        ObjectNode imageContent = OBJECT_MAPPER.createObjectNode();
        imageContent.put("type", "image_url");

        ObjectNode imageUrl = OBJECT_MAPPER.createObjectNode();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);

        imageContent.set("image_url", imageUrl);
        contentArray.add(imageContent);

        userMessage.set("content", contentArray);
        messages.add(userMessage);

        payload.put("model", deploymentName);
        payload.set("messages", messages);
        payload.put("max_completion_tokens", maxTokens);
        if (temperature != null) {
            payload.put("temperature", temperature);
        }

        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    /** Call Azure OpenAI Chat Completions API.
     * 
     * @param jsonPayload JSON request payload
     * @return Generated alt text
     * @throws AltTextGenerationException if API call fails */
    private String callAzureOpenAI(String jsonPayload) throws AltTextGenerationException {
        String url = endpoint;

        LOG.info("Calling Azure OpenAI API at URL: {}", url);
        LOG.debug("Request JSON payload: {}", jsonPayload);

        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader(HEADER_AUTHORIZATION, "Bearer " + apiKey);
        httpPost.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE);

        try {
            httpPost.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

            long startTime = System.currentTimeMillis();
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                LOG.info("Azure OpenAI API responded in {} ms with status {}", elapsedTime, statusCode);
                LOG.debug("Response body: {}", responseBody);

                if (statusCode >= 200 && statusCode < 300) {
                    // Parse response to extract alt text
                    JsonNode responseJson = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode choices = responseJson.path("choices");

                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode firstChoice = choices.get(0);
                        JsonNode message = firstChoice.path("message");

                        if (message.isObject()) {
                            String altText = message.path("content").asText("");
                            return altText.trim();
                        }
                    }

                    throw new AltTextGenerationException("Unexpected response format from Azure OpenAI: " + responseBody);
                } else {
                    LOG.error("Azure OpenAI API error (status {}) after {} ms: {}", statusCode, elapsedTime, responseBody);
                    throw new AltTextGenerationException("Azure OpenAI API returned error status: " + statusCode);
                }
            }
        } catch (IOException e) {
            throw new AltTextGenerationException("Error calling Azure OpenAI API", e);
        }
    }

    /** Get system resource resolver for DAM access.
     * 
     * @return ResourceResolver with system user privileges
     * @throws LoginException if login fails */
    private ResourceResolver getSystemResourceResolver() throws LoginException {
        return resourceResolverFactory.getServiceResourceResolver(
                Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SYSTEM_USER_NAME));
    }
}
