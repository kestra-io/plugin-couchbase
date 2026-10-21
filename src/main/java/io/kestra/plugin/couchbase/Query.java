package io.kestra.plugin.couchbase;

import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;

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
    title = "Run Couchbase N1QL and capture results",
    description = "Executes the rendered N1QL statement on the target cluster. Defaults to STORE, writing the full result set to Kestra internal storage; use FETCH to return all rows inline or FETCH_ONE for just the first row. Parameters can be named or positional."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a N1QL query to a Couchbase database",
            full = true,
            code = """
                id: couchbase_query
                namespace: company.team

                tasks:
                  - id: query
                    type: io.kestra.plugin.couchbase.Query
                    connectionString: couchbase://localhost
                    username: couchbase_user
                    password: "{{ secret('COUCHBASE_PASSWORD') }}"
                    query: SELECT * FROM `COUCHBASE_BUCKET`
                    fetchType: FETCH
                """
        ),
    }
)
public class Query extends CouchbaseConnection implements RunnableTask<Query.Output>, QueryInterface {
    private static final TypeRef<Map<String, Object>> MAP_TYPE_REF = new TypeRef<>() {
    };

    @NotNull
    @Builder.Default
    @PluginProperty(group = "processing")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);
    @PluginProperty(group = "main")
    protected Object parameters;

    @NotNull
    @NotBlank
    @PluginProperty(group = "main")
    protected String query;

    @Override
    public Output run(RunContext runContext) throws Exception {
        return run(runContext, cluster ->
        {
        });
    }

    Output run(RunContext runContext, Consumer<Cluster> onConnect) throws Exception {
        var session = connect(runContext);
        List<Map<String, Object>> rowsAsMap;
        try {
            onConnect.accept(session);
            var renderedQuery = runContext.render(query);
            var result = session.query(renderedQuery, getParametersForQuery());
            rowsAsMap = result.rowsAs(MAP_TYPE_REF);
        } finally {
            close(session);
        }

        var outputBuilder = Output.builder().size((long) rowsAsMap.size());
        return (switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> outputBuilder
                .rows(rowsAsMap);
            case FETCH_ONE -> outputBuilder
                .row(rowsAsMap.stream().findFirst().orElse(null));
            case STORE -> {
                File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    rowsAsMap.forEach(row ->
                    {
                        try {
                            FileSerde.write(outputStream, row);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                yield outputBuilder
                    .uri(runContext.storage().putFile(tempFile));
            }
            default -> outputBuilder;
        }).build();
    }

    private QueryOptions getParametersForQuery() {
        QueryOptions queryOptions = QueryOptions.queryOptions();

        if (parameters instanceof Map) {
            queryOptions.parameters(JsonObject.from((Map<String, ?>) parameters));
        } else if (parameters instanceof List) {
            queryOptions.parameters(JsonArray.from((List<?>) parameters));
        }

        return queryOptions;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "All rows returned",
            description = "Present when fetchType resolves to FETCH; each row is returned as a JSON map."
        )
        private List<Map<String, Object>> rows;

        @Schema(
            title = "First row returned",
            description = "Present when fetchType resolves to FETCH_ONE; null if the query returns no rows."
        )
        private Map<String, Object> row;

        @Schema(
            title = "Stored result URI",
            description = "Present when fetchType resolves to STORE; points to the Kestra internal storage object containing all rows."
        )
        private URI uri;

        @Schema(
            title = "Number of rows returned",
            description = "Set when fetchType is FETCH or STORE; useful for branching or triggers."
        )
        private Long size;
    }
}
