package biz.netcentric.aialttext.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.aialttext.exception.AltTextGenerationException;
import biz.netcentric.aialttext.service.AltTextGenerationService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowData;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;

/** Workflow process step for AI-powered alt text generation. Processes single DAM assets or folders recursively. */
@Component(service = WorkflowProcess.class, property = { "process.label=Generate AI Alt Text" })
public class ImageAltTextWorkflowStep implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(ImageAltTextWorkflowStep.class);
    private static final String DC_DESCRIPTION = "dc:description";
    private static final String DC_FORMAT = "dc:format";
    private static final String METADATA_PATH = com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT + "/metadata";
    private static final String PROMPT_TYPE_PARAM = "promptType";
    private static final String SERVICE_USER = "aialttext-service";

    @Reference
    private AltTextGenerationService altTextGenerationService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        LOG.info("Starting Generate AI Alt Text workflow step");

        WorkflowData workflowData = workItem.getWorkflowData();
        String payloadType = workflowData.getPayloadType();

        if (!"JCR_PATH".equals(payloadType)) {
            LOG.warn("Unsupported payload type: {}. Expected JCR_PATH.", payloadType);
            return;
        }

        String payloadPath = (String) workflowData.getPayload();
        if (StringUtils.isBlank(payloadPath)) {
            LOG.warn("Payload path is blank. Skipping workflow step.");
            return;
        }

        // Extract optional prompt type from workflow metadata
        String promptType = workflowData.getMetaDataMap().get(PROMPT_TYPE_PARAM, String.class);
        LOG.debug("Workflow prompt type: {}", promptType);

        Session session = workflowSession.getSession();
        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(
                    Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_USER));
        } catch (LoginException e) {
            throw new WorkflowException("Unable to obtain service resource resolver", e);
        }

        try {
            Resource resource = resourceResolver.getResource(payloadPath);
            if (resource == null) {
                LOG.warn("Resource not found at path: {}", payloadPath);
                return;
            }

            // Detect if payload is a single asset or a folder
            Asset asset = resource.adaptTo(Asset.class);
            List<Asset> assetsToProcess = new ArrayList<>();

            if (asset != null) {
                // Single asset
                LOG.info("Processing single asset: {}", payloadPath);
                assetsToProcess.add(asset);
            } else {
                // Folder - find all child image assets recursively
                LOG.info("Processing folder: {}", payloadPath);
                findImageAssets(resource, assetsToProcess);
                LOG.info("Found {} image assets in folder", assetsToProcess.size());
            }

            int processedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (Asset assetToProcess : assetsToProcess) {
                try {
                    boolean processed = processAsset(assetToProcess, session, promptType);
                    if (processed) {
                        processedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    LOG.error("Error processing asset: {}", assetToProcess.getPath(), e);
                }
            }

            // Save session if any assets were processed
            if (processedCount > 0) {
                session.save();
                LOG.info("Alt text generation completed. Processed: {}, Skipped: {}, Errors: {}", processedCount, skippedCount, errorCount);
            } else {
                LOG.info("No assets processed. Skipped: {}, Errors: {}", skippedCount, errorCount);
            }

        } catch (RepositoryException e) {
            throw new WorkflowException("Repository error during alt text generation", e);
        } catch (Exception e) {
            throw new WorkflowException("Error during alt text generation", e);
        } finally {
            if (resourceResolver != null && resourceResolver.isLive()) {
                resourceResolver.close();
            }
        }
    }

    /** Process a single asset: validate, extract language, generate alt text, write metadata.
     * 
     * @param asset Asset to process
     * @param session JCR session
     * @param promptType Optional prompt type from workflow metadata
     * @return true if asset was processed, false if skipped
     * @throws RepositoryException if JCR operations fail
     * @throws AltTextGenerationException if alt text generation fails */
    private boolean processAsset(Asset asset, Session session, String promptType) throws RepositoryException, AltTextGenerationException {
        String assetPath = asset.getPath();

        // Get metadata node
        Node assetNode = session.getNode(assetPath);
        if (!assetNode.hasNode(METADATA_PATH)) {
            LOG.warn("Asset has no metadata node: {}", assetPath);
            return false;
        }

        Node metadataNode = assetNode.getNode(METADATA_PATH);

        // Check if asset is an image
        String dcFormat = metadataNode.hasProperty(DC_FORMAT) ? metadataNode.getProperty(DC_FORMAT).getString() : "";
        if (StringUtils.isBlank(dcFormat) || !dcFormat.startsWith("image/")) {
            LOG.debug("Skipping non-image asset: {} (format: {})", assetPath, dcFormat);
            return false;
        }

        // Check if dc:description already exists
        if (metadataNode.hasProperty(DC_DESCRIPTION)) {
            String existingDescription = metadataNode.getProperty(DC_DESCRIPTION).getString();
            if (StringUtils.isNotBlank(existingDescription)) {
                LOG.debug("Skipping asset with existing description: {}", assetPath);
                return false;
            }
        }

        // Extract language from path
        String language = extractLanguageFromPath(assetPath);
        LOG.debug("Extracted language '{}' from path: {}", language, assetPath);

        // Generate alt text
        String altText = altTextGenerationService.generateAltText(asset, language, promptType);

        if (StringUtils.isNotBlank(altText)) {
            // Write to metadata
            metadataNode.setProperty(DC_DESCRIPTION, altText);
            LOG.info("Generated alt text for {}: {}", assetPath, altText);
            return true;
        } else {
            LOG.warn("Generated alt text is blank for asset: {}", assetPath);
            return false;
        }
    }

    /** Extract language code from DAM asset path based on configured segment index.
     * 
     * @param assetPath DAM asset path
     * @return Language code (e.g., "en", "fr", "de") */
    private String extractLanguageFromPath(String assetPath) {
        // Get configuration from service (default to index 5, default language "en")
        // Note: In production, this could be injected via OSGi config or obtained from the service
        int languageIndex = 5; // Default example: /content/dam/mysite/markets/gb/en/...
        String defaultLang = "en";

        String[] pathSegments = assetPath.split("/");

        if (pathSegments.length > languageIndex) {
            String languageSegment = pathSegments[languageIndex];
            // Validate it's a 2-letter language code
            if (languageSegment.matches("[a-z]{2}")) {
                return languageSegment;
            }
        }

        LOG.debug("Unable to extract language from path: {}. Using default: {}", assetPath, defaultLang);
        return defaultLang;
    }

    /** Recursively find all image assets under a resource (folder).
     * 
     * @param resource Parent resource
     * @param assets List to accumulate found assets */
    private void findImageAssets(Resource resource, List<Asset> assets) {
        // Check if current resource is an asset
        Asset asset = resource.adaptTo(Asset.class);
        if (asset != null) {
            // Check if it's an image
            String mimeType = (String) asset.getMetadata(DamConstants.DC_FORMAT);
            if (StringUtils.isNotBlank(mimeType) && mimeType.startsWith("image/")) {
                assets.add(asset);
            }
            return;
        }

        // Recurse into children
        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            Resource child = children.next();
            findImageAssets(child, assets);
        }
    }
}
