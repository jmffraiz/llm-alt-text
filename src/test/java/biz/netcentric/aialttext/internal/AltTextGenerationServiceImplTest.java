package biz.netcentric.aialttext.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import biz.netcentric.aialttext.exception.AltTextGenerationException;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;

@ExtendWith(MockitoExtension.class)
class AltTextGenerationServiceImplTest {

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private Asset asset;

    @Mock
    private Rendition rendition;

    private AltTextGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AltTextGenerationServiceImpl();
    }

    @Test
    void shouldThrowExceptionWhenAssetIsNull() {
        assertThatThrownBy(() -> service.generateAltText(null, "en", "alt-text"))
                .isInstanceOf(AltTextGenerationException.class)
                .hasMessageContaining("Asset cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenLanguageIsBlank() {
        assertThatThrownBy(() -> service.generateAltText(asset, "", "alt-text"))
                .isInstanceOf(AltTextGenerationException.class)
                .hasMessageContaining("Language cannot be blank");
    }

    @Test
    void shouldThrowExceptionWhenAssetHasNoOriginalRendition() {
        org.mockito.Mockito.when(asset.getRendition("original")).thenReturn(null);
        org.mockito.Mockito.when(asset.getPath()).thenReturn("/content/dam/test.jpg");

        assertThatThrownBy(() -> service.generateAltText(asset, "en", "alt-text"))
                .isInstanceOf(AltTextGenerationException.class)
                .hasMessageContaining("Asset has no original rendition");
    }

    @Test
    void shouldThrowExceptionWhenAssetIsNotAnImage() {
        org.mockito.Mockito.when(asset.getRendition("original")).thenReturn(rendition);
        org.mockito.Mockito.when(asset.getPath()).thenReturn("/content/dam/document.pdf");
        org.mockito.Mockito.when(rendition.getMimeType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> service.generateAltText(asset, "en", "alt-text"))
                .isInstanceOf(AltTextGenerationException.class)
                .hasMessageContaining("Asset is not an image");
    }

    @Test
    void shouldIncludeTemperatureInPayloadWhenConfigured() throws Exception {
        service.activate(configWithTemperature("0.2"));

        String payload = service.buildApiPayload("system prompt", "base64image", "image/png");

        assertThat(payload).contains("\"temperature\"");
        assertThat(payload).contains("0.2");
    }

    @Test
    void shouldOmitTemperatureFromPayloadWhenBlank() throws Exception {
        service.activate(configWithTemperature(""));

        String payload = service.buildApiPayload("system prompt", "base64image", "image/png");

        assertThat(payload).doesNotContain("\"temperature\"");
    }

    @Test
    void shouldOmitTemperatureFromPayloadWhenNullConfig() throws Exception {
        service.activate(configWithTemperature(null));

        String payload = service.buildApiPayload("system prompt", "base64image", "image/png");

        assertThat(payload).doesNotContain("\"temperature\"");
    }

    private AzureOpenAIConfiguration configWithTemperature(String temperature) {
        return new AzureOpenAIConfiguration() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AzureOpenAIConfiguration.class;
            }

            @Override
            public String apiKey() {
                return "test-api-key";
            }

            @Override
            public String endpoint() {
                return "https://test-endpoint.openai.azure.com/";
            }

            @Override
            public String deploymentName() {
                return "gpt-4o";
            }

            @Override
            public int maxTokens() {
                return 125;
            }

            @Override
            public String temperature() {
                return temperature;
            }

            @Override
            public int languagePathSegmentIndex() {
                return 5;
            }

            @Override
            public String defaultLanguage() {
                return "en";
            }

            @Override
            public String promptsBasePath() {
                return "/content/dam/aialttext/config/ai-prompts";
            }

            @Override
            public String defaultPromptType() {
                return "alt-text";
            }

            @Override
            public int promptCacheTtlSeconds() {
                return 3600;
            }

            @Override
            public int connectionTimeout() {
                return 5000;
            }

            @Override
            public int readTimeout() {
                return 10000;
            }

            @Override
            public int circuitBreakerFailureThreshold() {
                return 5;
            }

            @Override
            public long circuitBreakerResetTimeout() {
                return 60000L;
            }

            @Override
            public String fallbackPromptEn() {
                return "You generate SEO-focused alt text for images in a content management system.";
            }
        };
    }
}
