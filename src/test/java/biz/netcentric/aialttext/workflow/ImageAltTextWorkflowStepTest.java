package biz.netcentric.aialttext.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jcr.Session;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import biz.netcentric.aialttext.service.AltTextGenerationService;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowData;
import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.workflow.metadata.SimpleMetaDataMap;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/** Unit tests for ImageAltTextWorkflowStep.
 *
 * Note: Full integration tests with complex JCR/Asset mocking require more extensive setup. These basic tests validate the core workflow
 * step behavior without deep Asset/Node mocking. */
@ExtendWith({ AemContextExtension.class, MockitoExtension.class })
class ImageAltTextWorkflowStepTest {

    private static final String ASSET_PATH = "/content/dam/aialttext/markets/gb/en/test.jpg";
    private static final String PROCESS_ARGS = "PROCESS_ARGS";

    private final AemContext aemContext = new AemContext(ResourceResolverType.JCR_MOCK);

    @Mock
    private AltTextGenerationService altTextGenerationService;

    @Mock
    private WorkItem workItem;

    @Mock
    private WorkflowSession workflowSession;

    @Mock
    private WorkflowData workflowData;

    private ImageAltTextWorkflowStep workflowStep;
    private MetaDataMap metaDataMap;

    @BeforeEach
    void setUp() {
        workflowStep = new ImageAltTextWorkflowStep();
        aemContext.registerService(AltTextGenerationService.class, altTextGenerationService);
        aemContext.registerInjectActivateService(workflowStep);

        metaDataMap = new SimpleMetaDataMap();
    }

    @Test
    void shouldHandleNonJcrPathPayload() throws Exception {
        when(workItem.getWorkflowData()).thenReturn(workflowData);
        when(workflowData.getPayloadType()).thenReturn("SOME_OTHER_TYPE");

        workflowStep.execute(workItem, workflowSession, metaDataMap);

        verify(altTextGenerationService, never()).generateAltText(any(), any(), any());
    }

    @Test
    void shouldHandleNullPayload() throws Exception {
        when(workItem.getWorkflowData()).thenReturn(workflowData);
        when(workflowData.getPayloadType()).thenReturn("JCR_PATH");
        when(workflowData.getPayload()).thenReturn(null);

        workflowStep.execute(workItem, workflowSession, metaDataMap);

        verify(altTextGenerationService, never()).generateAltText(any(), any(), any());
    }

    @Test
    void shouldHandleBlankPayload() throws Exception {
        when(workItem.getWorkflowData()).thenReturn(workflowData);
        when(workflowData.getPayloadType()).thenReturn("JCR_PATH");
        when(workflowData.getPayload()).thenReturn("   ");

        workflowStep.execute(workItem, workflowSession, metaDataMap);

        verify(altTextGenerationService, never()).generateAltText(any(), any(), any());
    }

    @Test
    void shouldUsePromptTypeFromProcessArgs() throws Exception {
        // Given
        givenImageAssetPayload();
        metaDataMap.put(PROCESS_ARGS, "product-image");

        // When
        workflowStep.execute(workItem, workflowSession, metaDataMap);

        // Then
        verify(altTextGenerationService).generateAltText(any(), anyString(), eq("product-image"));
    }

    @Test
    void shouldPassNullPromptTypeWhenProcessArgsAbsent() throws Exception {
        // Given
        givenImageAssetPayload();

        // When
        workflowStep.execute(workItem, workflowSession, metaDataMap);

        // Then
        verify(altTextGenerationService).generateAltText(any(), anyString(), isNull());
    }

    @Test
    void shouldPassNullPromptTypeWhenProcessArgsIsBlank() throws Exception {
        // Given
        givenImageAssetPayload();
        metaDataMap.put(PROCESS_ARGS, "   ");

        // When
        workflowStep.execute(workItem, workflowSession, metaDataMap);

        // Then
        verify(altTextGenerationService).generateAltText(any(), anyString(), isNull());
    }

    /** Creates an image asset without dc:description and wires the workflow payload to point at it. */
    private void givenImageAssetPayload() throws Exception {
        aemContext.create().asset(ASSET_PATH, 100, 100, "image/jpeg");
        Resource metadata = aemContext.resourceResolver().getResource(ASSET_PATH + "/jcr:content/metadata");
        metadata.adaptTo(ModifiableValueMap.class).put("dc:format", "image/jpeg");
        aemContext.resourceResolver().commit();

        when(workItem.getWorkflowData()).thenReturn(workflowData);
        when(workflowData.getPayloadType()).thenReturn("JCR_PATH");
        when(workflowData.getPayload()).thenReturn(ASSET_PATH);
        when(workflowSession.getSession()).thenReturn(aemContext.resourceResolver().adaptTo(Session.class));
    }
}
