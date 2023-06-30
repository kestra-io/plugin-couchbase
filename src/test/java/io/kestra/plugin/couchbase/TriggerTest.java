package io.kestra.plugin.couchbase;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Worker;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.DefaultScheduler;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@MicronautTest
class TriggerTest extends CouchbaseTest {
    @Inject
    private ApplicationContext applicationContext;
    @Inject
    private FlowListenersInterface flowListenersService;
    @Inject
    private SchedulerTriggerStateInterface triggerState;
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;
    @Inject
    private LocalFlowRepositoryLoader localFlowRepositoryLoader;

    @Test
    void simpleQueryTrigger() throws Exception {
        Execution execution = triggerFlow();

        Map<String, Object> row = (Map<String, Object>) ((Map<String, Object>) execution.getTrigger().getVariables().get("row")).get(COLLECTION);
        assertThat(row.get("c_string"), is("A collection doc"));
    }

    protected Execution triggerFlow() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        Worker worker = new Worker(applicationContext, 8, null);
        try (
            AbstractScheduler scheduler = new DefaultScheduler(
                this.applicationContext,
                this.flowListenersService,
                this.triggerState
            );
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(execution -> {
                last.set(execution);

                queueCount.countDown();
                assertThat(execution.getFlowId(), is("couchbase-listen"));
            });

            worker.run();
            scheduler.run();

            localFlowRepositoryLoader.load(this.getClass().getClassLoader().getResource("flows/couchbase-listen.yml"));

            queueCount.await(1, TimeUnit.MINUTES);

            return last.get();
        }
    }
}
