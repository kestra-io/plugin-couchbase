package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Query a Couchbase database on regular intervals, and trigger flow on results."
)
@Plugin(
    examples = {
        @Example(
            title = "Wait for a N1QL query to return results, and then iterate through rows.",
            full = true,
            code = {
                "id: couchbase-trigger",
                "namespace: company.team",
                "",
                "tasks:",
                "  - id: each",
                "    type: io.kestra.plugin.core.flow.EachSequential",
                "    tasks:",
                "      - id: return",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{ json(taskrun.value) }}\"",
                "    value: \"{{ trigger.rows }}\"",
                "",
                "triggers:",
                "  - id: watch",
                "    type: io.kestra.plugin.couchbase.Trigger",
                "    interval: \"PT5M\"",
                "    connectionString: couchbase://localhost",
                "    username: couchbase_user",
                "    password: couchbase_passwd",
                "    query: SELECT * FROM `COUCHBASE_BUCKET`(.`COUCHBASE_SCOPE`.`COUCHBASE_COLLECTION`)",
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
    protected FetchType fetchType = FetchType.STORE;

    @NotNull
    @Builder.Default
    protected final Duration interval = Duration.ofSeconds(60);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        Query.Output run = Query.builder()
            .id(id)
            .type(Query.class.getName())
            .connectionString(connectionString)
            .username(username)
            .password(password)
            .query(query)
            .parameters(parameters)
            .fetchType(fetchType)
            .build().run(runContext);

        logger.debug("Found '{}' rows from '{}'", run.getSize(), runContext.render(this.query));

        if (run.getSize() == 0) {
            return Optional.empty();
        }

        Execution execution = TriggerService.generateExecution(this, conditionContext, context, run);

        return Optional.of(execution);
    }
}
