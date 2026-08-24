package biz.netcentric.aialttext.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private final AemContext aemContext = new AemContext();

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
}
