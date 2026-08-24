package biz.netcentric.aialttext.service;

import biz.netcentric.aialttext.exception.AltTextGenerationException;
import com.day.cq.dam.api.Asset;

/** Service for generating AI-powered alt text for DAM image assets using Azure OpenAI's vision model. */
public interface AltTextGenerationService {

    /** Generate alt text for a DAM asset using the specified prompt type.
     * 
     * @param asset The DAM asset (must be an image)
     * @param language Target language code (e.g., "en", "fr", "de")
     * @param promptType Prompt type identifier (e.g., "alt-text", "product-image"), null to use default from configuration
     * @return Generated alt text string
     * @throws AltTextGenerationException if generation fails */
    String generateAltText(Asset asset, String language, String promptType) throws AltTextGenerationException;

    /** Generate alt text using the default prompt type from configuration.
     * 
     * @param asset The DAM asset (must be an image)
     * @param language Target language code (e.g., "en", "fr", "de")
     * @return Generated alt text string
     * @throws AltTextGenerationException if generation fails */
    default String generateAltText(Asset asset, String language) throws AltTextGenerationException {
        return generateAltText(asset, language, null);
    }
}
