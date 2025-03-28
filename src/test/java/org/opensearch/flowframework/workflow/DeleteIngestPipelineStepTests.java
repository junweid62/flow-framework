/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.workflow;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.ingest.DeletePipelineRequest;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.opensearch.flowframework.common.WorkflowResources.PIPELINE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteIngestPipelineStepTests extends OpenSearchTestCase {
    private WorkflowData inputData;

    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private ClusterAdminClient clusterAdminClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        MockitoAnnotations.openMocks(this);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        inputData = new WorkflowData(Collections.emptyMap(), "test-id", "test-node-id");
    }

    public void testDeletePipeline() throws IOException, ExecutionException, InterruptedException {

        String pipelineId = randomAlphaOfLength(5);
        DeleteIngestPipelineStep deleteIngestPipelineStep = new DeleteIngestPipelineStep(client);

        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(clusterAdminClient).deletePipeline(any(DeletePipelineRequest.class), any());

        PlainActionFuture<WorkflowData> future = deleteIngestPipelineStep.execute(
            inputData.getNodeId(),
            inputData,
            Map.of("step_1", new WorkflowData(Map.of(PIPELINE_ID, pipelineId), "workflowId", "nodeId")),
            Map.of("step_1", PIPELINE_ID),
            Collections.emptyMap(),
            null
        );
        verify(clusterAdminClient).deletePipeline(any(DeletePipelineRequest.class), any());

        assertTrue(future.isDone());
        assertEquals(pipelineId, future.get().getContent().get(PIPELINE_ID));
    }

    public void testNoPipelineIdInOutput() throws IOException {
        DeleteIngestPipelineStep deleteIngestPipelineStep = new DeleteIngestPipelineStep(client);

        PlainActionFuture<WorkflowData> future = deleteIngestPipelineStep.execute(
            inputData.getNodeId(),
            inputData,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            null
        );

        assertTrue(future.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get().getContent());
        assertTrue(ex.getCause() instanceof FlowFrameworkException);
        assertEquals("Missing required inputs [pipeline_id] in workflow [test-id] node [test-node-id]", ex.getCause().getMessage());
    }

    public void testDeletePipelineFailure() throws IOException {
        DeleteIngestPipelineStep deleteIngestPipelineStep = new DeleteIngestPipelineStep(client);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new FlowFrameworkException("Failed", RestStatus.INTERNAL_SERVER_ERROR));
            return null;
        }).when(clusterAdminClient).deletePipeline(any(DeletePipelineRequest.class), any());

        PlainActionFuture<WorkflowData> future = deleteIngestPipelineStep.execute(
            inputData.getNodeId(),
            inputData,
            Map.of("step_1", new WorkflowData(Map.of(PIPELINE_ID, "test"), "workflowId", "nodeId")),
            Map.of("step_1", PIPELINE_ID),
            Collections.emptyMap(),
            null
        );

        verify(clusterAdminClient).deletePipeline(any(DeletePipelineRequest.class), any());

        assertTrue(future.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get().getContent());
        assertTrue(ex.getCause() instanceof FlowFrameworkException);
        assertEquals("Failed to delete the ingest pipeline test", ex.getCause().getMessage());
    }
}
