package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Query a Couchbase database on interval to trigger flow on results."
)
@Plugin(
    examples = {
        @Example(
            title = "Wait for a N1QL query to return results and iterate through rows",
            full = true,
            code = {
                "id: couchbase-trigger",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: each",
                "    type: io.kestra.core.tasks.flows.EachSequential",
                "    tasks:",
                "      - id: return",
                "        type: io.kestra.core.tasks.debugs.Return",
                "        format: \"{{json(taskrun.value)}}\"",
                "    value: \"{{ trigger.rows }}\"",
                "",
                "triggers:",
                "  - id: watch",
                "    type: io.kestra.plugin.couchbase.Trigger",
                "    interval: \"PT5M\"",
                "    connectionString: couchbase://localhost",
                "    username: couchbase_user",
                "    password: couchbase_passwd",
                "    query: SELECT * FROM COUCHBASE_BUCKET(.COUCHBASE_SCOPE.COUCHBASE_COLLECTION)",
                "    fetchType: FETCH"
            }
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Query.Output>, CouchbaseConnectionInterface, QueryInterface {
    @NotNull
    @NotBlank
    protected String connectionString;

    @NotNull
    @NotBlank
    protected String username;

    @NotNull
    @NotBlank
    protected String password;

    @NotNull
    @NotBlank
    protected String query;

    protected Object parameters;

    @NotNull
    @Builder.Default
    protected FetchType fetchType = FetchType.NONE;

    @NotNull
    @Builder.Default
    protected final Duration interval = Duration.ofSeconds(60);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        Query.Output queryOutput = Query.builder()
            .connectionString(connectionString)
            .username(username)
            .password(password)
            .query(query)
            .parameters(parameters)
            .fetchType(fetchType)
            .build().run(runContext);

        logger.debug("Found '{}' rows from '{}'", queryOutput.getSize(), runContext.render(this.query));

        if (queryOutput.getSize() == 0) {
            return Optional.empty();
        }

        String executionId = IdUtils.create();

        ExecutionTrigger executionTrigger = ExecutionTrigger.of(
            this,
            queryOutput
        );

        Execution execution = Execution.builder()
            .id(executionId)
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            .build();

        return Optional.of(execution);
    }
}