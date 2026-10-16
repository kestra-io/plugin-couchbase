package io.kestra.plugin.couchbase;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Poll Couchbase query and start flow",
    description = "Periodically runs the rendered N1QL query and starts the flow when rows are returned. Default interval is 60 seconds and fetchType defaults to STORE, writing results to Kestra internal storage; use FETCH or FETCH_ONE to expose rows inline."
)
@Plugin(
    examples = {
        @Example(
            title = "Wait for a N1QL query to return results, and then iterate through rows.",
            full = true,
            code = """
                id: couchbase_trigger
                namespace: company.team

                tasks:
                  - id: each
                    type: io.kestra.plugin.core.flow.ForEach
                    values: "{{ trigger.rows }}"
                    tasks:
                      - id: return
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ fromJson(taskrun.value) }}"

                triggers:
                  - id: watch
                    type: io.kestra.plugin.couchbase.Trigger
                    interval: "PT5M"
                    connectionString: couchbase://localhost
                    username: couchbase_user
                    password: "{{ secret('COUCHBASE_PASSWORD') }}"
                    query: SELECT * FROM `COUCHBASE_BUCKET`.`COUCHBASE_SCOPE`.`COUCHBASE_COLLECTION`
                    fetchType: FETCH
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Query.Output>, CouchbaseConnectionInterface, QueryInterface {
    @NotNull
    @NotBlank
    protected String connectionString;

    @NotNull
    @NotBlank
    @PluginProperty(secret = true, dynamic = true, group = "connection")
    protected String username;

    @NotNull
    @NotBlank
    @ToString.Exclude
    @PluginProperty(secret = true, dynamic = true, group = "connection")
    protected String password;

    @NotNull
    @NotBlank
    protected String query;

    protected Object parameters;

    @NotNull
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @NotNull
    @Builder.Default
    protected final Duration interval = Duration.ofSeconds(60);

    private final transient Lock lifecycleLock = new ReentrantLock();

    @Getter(AccessLevel.NONE)
    private final transient AtomicBoolean killed = new AtomicBoolean(false);

    @Getter(AccessLevel.NONE)
    private final transient AtomicReference<com.couchbase.client.java.Cluster> activeCluster = new AtomicReference<>();

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        // Check killed flag before starting any work
        if (killed.get()) {
            return Optional.empty();
        }

        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        Query queryTask = Query.builder()
            .id(id)
            .type(Query.class.getName())
            .connectionString(connectionString)
            .username(username)
            .password(password)
            .query(query)
            .parameters(parameters)
            .fetchType(fetchType)
            .build();
        queryTask.setTrigger(this);

        Query.Output run = queryTask.run(runContext);

        // If killed during evaluation, don't generate execution
        if (killed.get()) {
            return Optional.empty();
        }

        logger.debug("Found '{}' rows from '{}'", run.getSize(), runContext.render(this.query));

        if (run.getSize() == 0) {
            return Optional.empty();
        }

        Execution execution = TriggerService.generateExecution(this, conditionContext, context, run);

        return Optional.of(execution);
    }

    @Override
    public void kill() {
        // 1. Set killed flag FIRST (outside lock) - allows setActiveCluster to quickly detect
        killed.set(true);

        // 2. Now take the active cluster under lock - no one can publish after we took it
        com.couchbase.client.java.Cluster cluster;
        try {
            lifecycleLock.lock();
            cluster = activeCluster.getAndSet(null);
        } finally {
            lifecycleLock.unlock();
        }

        // 3. Disconnect outside lock (non-blocking, async)
        if (cluster != null) {
            try {
                cluster.async().disconnect();
            } catch (Exception ignored) {
                // Best effort disconnect
            }
        }
    }

    void setActiveCluster(com.couchbase.client.java.Cluster cluster) {
        com.couchbase.client.java.Cluster toDisconnect = null;
        try {
            lifecycleLock.lock();
            if (killed.get()) {
                // Kill already won - disconnect this cluster immediately
                toDisconnect = cluster;
            } else {
                activeCluster.set(cluster);
            }
        } finally {
            lifecycleLock.unlock();
        }
        // Disconnect outside lock (non-blocking)
        if (toDisconnect != null) {
            try {
                toDisconnect.async().disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    void clearActiveCluster() {
        try {
            lifecycleLock.lock();
            activeCluster.set(null);
        } finally {
            lifecycleLock.unlock();
        }
    }

    // Test accessor
    boolean isKilledForTest() {
        return killed.get();
    }

    com.couchbase.client.java.Cluster getActiveClusterForTest() {
        return activeCluster.get();
    }
}
