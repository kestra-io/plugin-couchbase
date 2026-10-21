package io.kestra.plugin.couchbase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.couchbase.client.java.AsyncCluster;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@KestraTest
class TriggerKillTest {
    @Inject
    private RunContextFactory runContextFactory;

    private Trigger trigger;
    private Cluster cluster;
    private AsyncCluster asyncCluster;
    private ClusterEnvironment environment;
    private RunContext runContext;
    private ConditionContext conditionContext;
    private Logger logger;

    @BeforeEach
    void setUp() {
        trigger = Trigger.builder()
            .id(IdUtils.create())
            .type(Trigger.class.getName())
            .connectionString("couchbase://localhost")
            .username("Administrator")
            .password("password")
            .query("SELECT 1")
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();
        cluster = mock(Cluster.class);
        asyncCluster = mock(AsyncCluster.class);
        environment = mock(ClusterEnvironment.class);
        when(cluster.async()).thenReturn(asyncCluster);
        when(cluster.environment()).thenReturn(environment);
        when(asyncCluster.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        runContext = spy(runContextFactory.of());
        logger = mock(Logger.class);
        doReturn(logger).when(runContext).logger();
        conditionContext = mock(ConditionContext.class);
        when(conditionContext.getRunContext()).thenReturn(runContext);
    }

    @Test
    void killBeforeEvaluationIsIdempotentAndSkipsConnection() throws Exception {
        trigger.kill();
        trigger.kill();
        try (var connections = mockStatic(Cluster.class)) {
            assertEquals(Optional.empty(), trigger.evaluate(conditionContext, null));
            connections.verifyNoInteractions();
            verify(logger).warn("Skipping evaluation of killed Couchbase trigger '{}'", trigger.getId());
        }
    }

    @Test
    void normalEmptyEvaluationClosesResources() throws Exception {
        var result = mock(QueryResult.class);
        when(result.rowsAs(any(TypeRef.class))).thenReturn(List.of());
        when(cluster.query(anyString(), any(QueryOptions.class))).thenReturn(result);
        evaluateWithCluster();
        evaluateWithCluster();
        trigger.kill();
        verify(cluster, times(2)).disconnect();
        verify(environment, times(2)).shutdown();
        verifyNoInteractions(asyncCluster);
    }

    @Test
    void queryFailureClosesResourcesAndPropagates() {
        var failure = new IllegalStateException("query failed");
        when(cluster.query(anyString(), any(QueryOptions.class))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, this::evaluateWithCluster));
        trigger.kill();
        verify(cluster).disconnect();
        verify(environment).shutdown();
        verifyNoInteractions(asyncCluster);
    }

    @Test
    void killDuringConnectionDisconnectsLateClusterAndSkipsQuery() throws Exception {
        try (
            var environments = mockStatic(ClusterEnvironment.class);
            var connections = mockStatic(Cluster.class)
        ) {
            environments.when(ClusterEnvironment::create).thenReturn(environment);
            connections.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenAnswer(invocation ->
            {
                trigger.kill();
                return cluster;
            });
            assertEquals(Optional.empty(), trigger.evaluate(conditionContext, null));
        }
        verify(asyncCluster).disconnect();
        verify(environment).shutdownAsync();
        verify(cluster, never()).query(anyString(), any(QueryOptions.class));
        verify(cluster).disconnect();
        verify(environment).shutdown();
    }

    @Test
    void killUnblocksQueryAndSuppressesCancellationFailure() throws Exception {
        var querying = new CountDownLatch(1);
        var disconnected = new CountDownLatch(1);
        when(asyncCluster.disconnect()).thenAnswer(invocation ->
        {
            disconnected.countDown();
            return CompletableFuture.completedFuture(null);
        });
        when(cluster.query(anyString(), any(QueryOptions.class))).thenAnswer(invocation ->
        {
            querying.countDown();
            assertTrue(disconnected.await(5, TimeUnit.SECONDS));
            throw new IllegalStateException("cluster disconnected");
        });
        var killer = CompletableFuture.runAsync(() ->
        {
            try {
                assertTrue(querying.await(5, TimeUnit.SECONDS));
                trigger.kill();
                trigger.kill();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        evaluateWithCluster();
        killer.get(5, TimeUnit.SECONDS);
        verify(asyncCluster).disconnect();
        verify(environment).shutdownAsync();
        verify(cluster).disconnect();
        verify(environment).shutdown();
    }

    @Test
    void killDuringSuccessfulQuerySuppressesExecution() throws Exception {
        var result = mock(QueryResult.class);
        when(result.rowsAs(any(TypeRef.class))).thenReturn(List.of(Map.of("value", 1)));
        when(cluster.query(anyString(), any(QueryOptions.class))).thenAnswer(invocation ->
        {
            trigger.kill();
            return result;
        });
        evaluateWithCluster();
        verify(asyncCluster).disconnect();
        verify(environment).shutdownAsync();
    }

    @Test
    void failedAsyncDisconnectStillShutsDownEnvironment() throws Exception {
        var failure = new IllegalStateException("disconnect failed");
        var disconnect = new CompletableFuture<Void>();
        when(asyncCluster.disconnect()).thenReturn(disconnect);
        when(cluster.query(anyString(), any(QueryOptions.class))).thenAnswer(invocation ->
        {
            trigger.kill();
            throw new IllegalStateException("cancelled");
        });
        evaluateWithCluster();
        CompletableFuture.runAsync(() -> disconnect.completeExceptionally(failure)).get(5, TimeUnit.SECONDS);
        verify(logger).warn("Failed to disconnect killed Couchbase trigger", failure);
        verify(environment).shutdownAsync();
        verify(environment).shutdown();
    }

    @Test
    void killDoesNotWaitForAsyncDisconnect() throws Exception {
        var disconnect = new CompletableFuture<Void>();
        when(asyncCluster.disconnect()).thenReturn(disconnect);
        when(cluster.query(anyString(), any(QueryOptions.class))).thenAnswer(invocation ->
        {
            trigger.kill();
            assertFalse(disconnect.isDone());
            verify(environment, never()).shutdownAsync();
            disconnect.complete(null);
            throw new IllegalStateException("cancelled");
        });
        evaluateWithCluster();
        verify(environment).shutdownAsync();
    }

    @Test
    void synchronousDisconnectFailureStillShutsDownEnvironment() throws Exception {
        var failure = new IllegalStateException("disconnect failed");
        when(asyncCluster.disconnect()).thenThrow(failure);
        when(cluster.query(anyString(), any(QueryOptions.class))).thenAnswer(invocation ->
        {
            trigger.kill();
            throw new IllegalStateException("cancelled");
        });
        evaluateWithCluster();
        verify(environment).shutdownAsync();
        verify(logger).warn("Failed to disconnect killed Couchbase trigger", failure);
    }

    private void evaluateWithCluster() throws Exception {
        try (
            var environments = mockStatic(ClusterEnvironment.class);
            var connections = mockStatic(Cluster.class)
        ) {
            environments.when(ClusterEnvironment::create).thenReturn(environment);
            connections.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);
            assertEquals(Optional.empty(), trigger.evaluate(conditionContext, null));
        }
    }
}
