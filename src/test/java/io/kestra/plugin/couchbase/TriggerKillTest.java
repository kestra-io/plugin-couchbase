package io.kestra.plugin.couchbase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.couchbase.client.java.AsyncCluster;
import com.couchbase.client.java.Cluster;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class TriggerKillTest {

    private Trigger trigger;

    @BeforeEach
    void setUp() {
        trigger = Trigger.builder()
            .id("test-trigger")
            .type(Trigger.class.getName())
            .connectionString("couchbase://localhost")
            .username("Administrator")
            .password("password")
            .query("SELECT * FROM bucket")
            .build();
    }

    @Test
    void killWithNoActiveClusterDoesNotThrow() {
        // kill() should not throw when no cluster is active
        trigger.kill();
        // If we get here without exception, the test passes
    }

    @Test
    void killBeforeEvaluationDoesNotThrow() {
        // kill() before any evaluation should not throw
        trigger.kill();
        // If we get here without exception, the test passes
    }

    @Test
    void killIsIdempotent() {
        // Multiple calls to kill() should not throw
        trigger.kill();
        trigger.kill();
        trigger.kill();
        // If we get here without exception, the test passes
    }

    @Test
    void activeClusterIsInitiallyNull() {
        // The active cluster reference should be null initially
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }

    @Test
    void killedFlagIsInitiallyFalse() {
        // The killed flag should be false initially
        assertThat(trigger.isKilledForTest(), is(false));
    }

    @Test
    void killSetsKilledFlag() {
        // kill() should set the killed flag
        assertThat(trigger.isKilledForTest(), is(false));
        trigger.kill();
        assertThat(trigger.isKilledForTest(), is(true));
    }

    @Test
    void killClearsActiveClusterReference() throws Exception {
        // Set a mock cluster
        Cluster mockCluster = mock(Cluster.class);
        AsyncCluster mockAsyncCluster = mock(AsyncCluster.class);
        when(mockCluster.async()).thenReturn(mockAsyncCluster);
        trigger.setActiveCluster(mockCluster);

        // Verify it's set
        assertThat(trigger.getActiveClusterForTest(), sameInstance(mockCluster));

        // Call kill
        trigger.kill();

        // Verify it's cleared
        assertThat(trigger.getActiveClusterForTest(), nullValue());

        // Verify the cluster was actually disconnected via the non-blocking async API
        verify(mockAsyncCluster).disconnect();
    }

    @Test
    void killDuringActiveQueryUnblocksCluster() throws Exception {
        // Test that kill() disconnects the cluster when one is active
        Cluster mockCluster = mock(Cluster.class);
        AsyncCluster mockAsyncCluster = mock(AsyncCluster.class);
        when(mockCluster.async()).thenReturn(mockAsyncCluster);
        trigger.setActiveCluster(mockCluster);

        // Call kill from another thread to simulate concurrent access
        Thread killThread = new Thread(() -> trigger.kill());
        killThread.start();
        killThread.join(1000);

        // Verify the cluster was actually disconnected via the non-blocking async API
        verify(mockAsyncCluster).disconnect();

        // Verify cluster reference is cleared
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }

    @Test
    void clearActiveClusterClearsReference() {
        // Set a mock cluster
        Cluster mockCluster = mock(Cluster.class);
        trigger.setActiveCluster(mockCluster);

        // Verify it's set
        assertThat(trigger.getActiveClusterForTest(), sameInstance(mockCluster));

        // Call clearActiveCluster
        trigger.clearActiveCluster();

        // Verify it's cleared
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }

    @Test
    void killBeforeClusterPublicationDoesNotLoseCancellation() throws Exception {
        // kill() before setActiveCluster() should not lose the cancellation
        trigger.kill();

        // Now publish a cluster - it should be immediately disconnected
        Cluster mockCluster = mock(Cluster.class);
        AsyncCluster mockAsyncCluster = mock(AsyncCluster.class);
        when(mockCluster.async()).thenReturn(mockAsyncCluster);
        trigger.setActiveCluster(mockCluster);

        // The cluster should have been actually disconnected via the non-blocking async API
        verify(mockAsyncCluster).disconnect();

        // Active cluster should remain null
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }

    @Test
    void concurrentKillAndClusterPublicationNeverLosesCancellation() throws Exception {
        // Test the critical race: kill() happens concurrently with setActiveCluster().
        // The latches below only guarantee both threads are in flight at the same time;
        // they do not control which thread acquires the lifecycle lock first, so this test
        // does not prove a specific kill-wins interleaving. Instead it proves the property
        // that holds for every interleaving: exactly one side disconnects the cluster and
        // the active cluster reference ends up null, so cancellation is never lost.

        CountDownLatch killStarted = new CountDownLatch(1);
        CountDownLatch setClusterStarted = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        Cluster mockCluster = mock(Cluster.class);
        AsyncCluster mockAsyncCluster = mock(AsyncCluster.class);
        when(mockCluster.async()).thenReturn(mockAsyncCluster);

        // Thread 1: calls kill()
        Thread killThread = new Thread(() ->
        {
            try {
                killStarted.countDown(); // Signal kill started
                setClusterStarted.await(); // Wait for setActiveCluster to start
                trigger.kill();
            } catch (Exception e) {
                exceptionRef.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: calls setActiveCluster()
        Thread setClusterThread = new Thread(() ->
        {
            try {
                killStarted.await(); // Wait for kill to start
                setClusterStarted.countDown(); // Signal setActiveCluster started
                trigger.setActiveCluster(mockCluster);
            } catch (Exception e) {
                exceptionRef.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        killThread.start();
        setClusterThread.start();

        assertThat(doneLatch.await(5, TimeUnit.SECONDS), is(true));
        assertThat(exceptionRef.get(), nullValue());

        // Whichever side won, the cluster was actually disconnected via the non-blocking
        // async API exactly once: either kill() took the published cluster, or
        // setActiveCluster() saw the killed flag and disconnected the late cluster.
        verify(mockAsyncCluster).disconnect();

        // The active cluster reference must never be left populated after a kill.
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }

    @Test
    void evaluateAfterKillDoesNotGenerateExecution() throws Exception {
        // kill() before evaluation should prevent execution generation
        trigger.kill();

        // We can't easily test the full evaluate() without a real Couchbase instance,
        // but we can verify the killed flag prevents work
        assertThat(trigger.isKilledForTest(), is(true));
    }

    @Test
    void killReturnsPromptly() throws Exception {
        // kill() should return quickly and not block
        Cluster mockCluster = mock(Cluster.class);
        AsyncCluster mockAsyncCluster = mock(AsyncCluster.class);
        when(mockCluster.async()).thenReturn(mockAsyncCluster);
        trigger.setActiveCluster(mockCluster);

        long start = System.currentTimeMillis();
        trigger.kill();
        long elapsed = System.currentTimeMillis() - start;

        // kill() should return in well under 100ms (async disconnect is non-blocking)
        assertThat(elapsed, lessThan(100L));

        // And the disconnect must have actually been issued via the async API
        verify(mockAsyncCluster).disconnect();
    }

    @Test
    void multipleKillCallsAreSafe() {
        // Multiple kill() calls should be safe (idempotent)
        trigger.kill();
        trigger.kill();
        trigger.kill();

        // All calls should complete without exception
        assertThat(trigger.isKilledForTest(), is(true));
    }

    @Test
    void normalCompletionClearsActiveReference() {
        // After normal query completion (simulated by clearActiveCluster), reference should be null
        Cluster mockCluster = mock(Cluster.class);
        trigger.setActiveCluster(mockCluster);
        assertThat(trigger.getActiveClusterForTest(), sameInstance(mockCluster));

        trigger.clearActiveCluster();
        assertThat(trigger.getActiveClusterForTest(), nullValue());
    }
}
