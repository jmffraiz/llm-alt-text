# AEM AI Alt Text Generator

An AEM workflow step + OSGi service that generates image `alt` text with an LLM vision
model, built for automating accessibility/SEO metadata on
DAM assets. Companion code for the adaptTo() 2026 talk.

## What it does

1. A DAM workflow step (`ImageAltTextWorkflowStep`, process label **"Generate AI Alt
   Text"**) is triggered on a single asset or a folder of assets.
2. For each image asset without an existing `dc:description`, it calls
   `AltTextGenerationService`, which:
   - Reads the asset's original rendition and base64-encodes it.
   - Loads a system prompt (from DAM, with an in-memory TTL cache and a hardcoded
     fallback) based on a `promptType` (defaults to `alt-text`, but can be set per
     workflow via metadata, e.g. `product-image`, `campaign`).
   - Sends the prompt + image to Azure OpenAI's Chat Completions API (vision-capable
     deployment) behind a resilience4j circuit breaker.
   - Writes the returned text back to `jcr:content/metadata/dc:description`.
3. The language for the prompt is derived from a configurable path segment index (e.g.
   `/content/dam/mysite/markets/gb/en/...` → `en`), with a configurable default.

## Configuration

All runtime settings are exposed via the `AzureOpenAIConfiguration` OSGi configuration
(`biz.netcentric.aialttext.internal.AzureOpenAIConfiguration`):

| Property | Description |
|---|---|
| `apiKey` | Azure OpenAI API key |
| `endpoint` | Full chat-completions URL for your Azure OpenAI deployment |
| `deploymentName` | Model deployment name |
| `apiVersion` | API version |
| `maxTokens` | Generation control (max tokens) |
| `temperature` | Generation control (blank = omit from request) |
| `promptsBasePath` | Base path for prompt loading from DAM |
| `defaultPromptType` | Default prompt type |
| `promptCacheTtlSeconds` | Prompt cache TTL |
| `languagePathSegmentIndex` | Path segment index used to infer the target language from the asset path |
| `defaultLanguage` | Default language when it cannot be inferred |
| `connectionTimeout` | HTTP client connection timeout |
| `readTimeout` | HTTP client read timeout |
| `circuitBreakerFailureThreshold` | resilience4j circuit breaker failure threshold |
| `circuitBreakerResetTimeout` | resilience4j circuit breaker reset timeout |

A dedicated service user (subservice name `aialttext-service`) should be mapped via
`org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl` and granted read access
to the DAM and read/write access to asset metadata.

### Storing prompts in the DAM

System prompts are plain text assets uploaded to the DAM under `promptsBasePath`
(default `/content/dam/aialttext/config/ai-prompts`). Each prompt must follow the naming
convention:

```
{promptType}-{language}.txt
```

For example, `alt-text-en.txt`, `alt-text-fr.txt`, or `product-image-de.txt` under
`/content/dam/aialttext/config/ai-prompts/`. The text content of the file's `original`
rendition is sent verbatim as the system prompt.

At runtime, the two placeholders are resolved as follows:

- **`{promptType}`** — taken from the `promptType` workflow metadata set on the workflow
  model/step (see [What it does](#what-it-does) above), or `defaultPromptType` when not set.
- **`{language}`** — extracted from the asset's path using `languagePathSegmentIndex`
  (e.g. `/content/dam/mysite/markets/gb/en/...` → `en`), falling back to
  `defaultLanguage` if the segment can't be determined.

Neither value is something an author chooses when generating alt text — both are
resolved automatically and used as a lookup key to select the matching prompt asset. The
language still needs its own file per locale (rather than one prompt with the language
name substituted in) because the prompt content itself is expected to be authored/
translated per language, not just parameterized.

`AltTextGenerationServiceImpl` builds the path
`{promptsBasePath}/{promptType}-{language}.txt`, reads it via the
`aialttext-service` service user, and caches the content in memory for
`promptCacheTtlSeconds` (keyed by `{promptType}-{language}`) to avoid a repository read on
every asset. The cache is cleared whenever the OSGi configuration is updated or the
service is deactivated.

If no matching prompt asset exists in the DAM (or it can't be read), the service falls
back to the hardcoded `fallbackPromptEn` configuration property, with the literal text
`Output language: English` replaced by the resolved language — so at minimum a fallback
prompt always exists, even before any prompts are authored in the DAM.

## Build

```bash
mvn clean install
```

Requires Java 21 and access to Adobe's public Maven repository (configured in
[pom.xml](pom.xml)) for the AEM `uber-jar` API dependency.

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).
