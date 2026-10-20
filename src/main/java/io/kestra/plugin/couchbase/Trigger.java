package io.kestra.plugin.couchbase;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.java.Cluster;

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

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient Lock lifecycleLock = new ReentrantLock();

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient AtomicBoolean killed = new AtomicBoolean(false);

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient AtomicReference<Cluster> activeCluster = new AtomicReference<>();

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        if (killed.get()) {
            LoggerFactory.getLogger(Trigger.class).warn("Skipping evaluation of killed Couchbase trigger '{}'", id);
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
        Query.Output run;
        try {
            run = queryTask.run(runContext, this::setActiveCluster);
        } catch (Exception e) {
            if (killed.get()) {
                return Optional.empty();
            }
            throw e;
        } finally {
            clearActiveCluster();
        }

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
        killed.set(true);
        Cluster cluster;
        lifecycleLock.lock();
        try {
            cluster = activeCluster.getAndSet(null);
        } finally {
            lifecycleLock.unlock();
        }
        if (cluster != null) {
            disconnect(cluster);
        }
    }

    private void setActiveCluster(Cluster cluster) {
        boolean cancelled;
        lifecycleLock.lock();
        try {
            cancelled = killed.get();
            if (!cancelled) {
                activeCluster.set(cluster);
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (cancelled) {
            disconnect(cluster);
            throw new IllegalStateException("Couchbase trigger was killed");
        }
    }

    private void disconnect(Cluster cluster) {
        try {
            cluster.async().disconnect().whenComplete((result, error) ->
            {
                if (error != null) {
                    LoggerFactory.getLogger(Trigger.class).warn("Failed to disconnect killed Couchbase trigger", error);
                }
                cluster.environment().shutdownAsync();
            });
        } catch (Exception e) {
            LoggerFactory.getLogger(Trigger.class).warn("Failed to disconnect killed Couchbase trigger", e);
            cluster.environment().shutdownAsync();
        }
    }

    private void clearActiveCluster() {
        lifecycleLock.lock();
        try {
            activeCluster.set(null);
        } finally {
            lifecycleLock.unlock();
        }
    }
}
