package biz.netcentric.aialttext.internal;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/** OSGi Configuration for Azure OpenAI Alt Text Generation Service. This configuration allows runtime configuration of Azure OpenAI
 * integration settings for AI-powered image alt text generation. */
@ObjectClassDefinition(name = "AI Alt Text - Azure OpenAI Configuration", description = "Configuration for Azure OpenAI alt text generation integration")
public @interface AzureOpenAIConfiguration {

    @AttributeDefinition(name = "Azure OpenAI API Key", description = "Azure OpenAI API key for authentication", type = AttributeType.PASSWORD)
    String apiKey() default "";

    @AttributeDefinition(name = "Azure OpenAI Endpoint", description = "Azure OpenAI endpoint URL, including deployment path (e.g., https://<your-resource>.openai.azure.com/openai/v1/chat/completions)", type = AttributeType.STRING)
    String endpoint() default "https://your-resource.openai.azure.com/openai/v1/chat/completions";

    @AttributeDefinition(name = "Deployment Name", description = "Azure OpenAI deployment/model name (e.g., gpt-4o)", type = AttributeType.STRING)
    String deploymentName() default "gpt-4o";

    @AttributeDefinition(name = "API Version", description = "Azure OpenAI API version", type = AttributeType.STRING)
    String apiVersion() default "2024-10-21";

    @AttributeDefinition(name = "Max Tokens", description = "Maximum number of tokens for alt text generation", type = AttributeType.INTEGER)
    int maxTokens() default 8192;

    @AttributeDefinition(name = "Temperature", description = "Temperature for AI generation (0.0-1.0, lower is more focused). Leave blank to omit from the request (API default will be used). Example: 0.2", type = AttributeType.STRING)
    String temperature() default "";

    @AttributeDefinition(name = "Language Path Segment Index", description = "0-based index of the language code segment in DAM paths (e.g., for /content/dam/mysite/markets/gb/en/..., index=5 extracts 'en')", type = AttributeType.INTEGER)
    int languagePathSegmentIndex() default 5;

    @AttributeDefinition(name = "Default Language", description = "Default language code when path parsing fails (e.g., 'en', 'fr', 'de')", type = AttributeType.STRING)
    String defaultLanguage() default "en";

    @AttributeDefinition(name = "Prompts DAM Base Path", description = "DAM folder containing AI prompt text files (e.g., /content/dam/aialttext/config/ai-prompts)", type = AttributeType.STRING)
    String promptsBasePath() default "/content/dam/aialttext/config/ai-prompts";

    @AttributeDefinition(name = "Default Prompt Type", description = "Default prompt type identifier (e.g., 'alt-text', 'product-image', 'lifestyle-image')", type = AttributeType.STRING)
    String defaultPromptType() default "alt-text";

    @AttributeDefinition(name = "Prompt Cache TTL (seconds)", description = "Time-to-live for cached prompts in memory", type = AttributeType.INTEGER)
    int promptCacheTtlSeconds() default 3600;

    @AttributeDefinition(name = "Connection Timeout", description = "HTTP connection timeout in milliseconds", type = AttributeType.INTEGER)
    int connectionTimeout() default 5000;

    @AttributeDefinition(name = "Read Timeout", description = "HTTP read timeout in milliseconds", type = AttributeType.INTEGER)
    int readTimeout() default 10000;

    @AttributeDefinition(name = "Circuit Breaker Failure Threshold", description = "Number of consecutive failures before opening circuit", type = AttributeType.INTEGER)
    int circuitBreakerFailureThreshold() default 5;

    @AttributeDefinition(name = "Circuit Breaker Reset Timeout", description = "Time in milliseconds before attempting to close circuit after opening", type = AttributeType.LONG)
    long circuitBreakerResetTimeout() default 60000L;

    @AttributeDefinition(name = "Fallback Prompt (English)", description = "Fallback system prompt used when a prompt cannot be loaded from DAM. The literal text 'Output language: English' is replaced with the requested language at runtime.", type = AttributeType.STRING)
    String fallbackPromptEn() default "You generate SEO-focused alt text for images in a content management system. "
            + "Primary goal is search indexing, with accessibility-compatible output. "
            + "\n\nOutput language: English. Generate the alt tag directly in this language. "
            + "\n\nBrand names, product names, and model identifiers are proper nouns and immutable tokens. "
            + "Do NOT translate, localize, transliterate, rewrite, normalize, or modify them in any way. "
            + "Preserve original spelling, capitalization, and punctuation exactly as visible. "
            + "If there is any conflict, preservation of proper nouns takes priority over output language rules. "
            + "\n\nIf the product or feature name already contains the brand name, do not add a separate brand. "
            + "Include the brand only once. "
            + "\n\nGenerate an SEO-friendly alt tag including brand (if visible), product or feature name, and a 5–12 word SEO description. "
            + "\n\nInclude brand name ONLY if it is clearly visible and readable. "
            + "Do NOT guess or infer brand, model, or specifications. "
            + "\n\nIf the image does NOT show a recognizable product: "
            + "generate an SEO-friendly, neutral, human-readable descriptive. "
            + "\n\nRules: SEO clarity over visual detail, no guessing of brand or model, no repetition, "
            + "avoid full sentences, avoid marketing language, under 125 characters, output ONLY the final alt tag.";
}
