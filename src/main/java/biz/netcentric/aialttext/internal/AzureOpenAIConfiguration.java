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
    String fallbackPromptEn() default 
        "You generate concise, accurate alt text for consumer goods e-commerce images.\n"
        + "Output language: English. Generate the alt text directly in this language.\n"
        + "Return ONLY the final alt text; never provide explanations, analysis, labels, or markdown.\n"
        + "For product images, prioritize brand, product/model, product type, variant, and relevant visible specifications.\n"
        + "Include important specifications such as quantity, size, capacity, weight, or strength when clearly visible.\n"
        + "For lifestyle or promotional images, describe the main meaningful subject and context, not every visual detail.\n"
        + "Include meaningful text from the image when relevant, reproducing it verbatim; do not transcribe irrelevant text.\n"
        + "Exclude legal, regulatory, and warning text unless essential to understanding the image.\n"
        + "Keep alt text concise: typically 5–25 words; exceed this only when necessary to convey important information.\n"
        + "Write for accessibility first and SEO second; use natural language and avoid keyword stuffing.\n"
        + "Never invent information; if decorative, return exactly \"\".\n";
 
}
