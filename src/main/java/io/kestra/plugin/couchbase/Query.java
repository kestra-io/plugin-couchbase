package io.kestra.plugin.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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

import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Query a Couchbase database with N1QL."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a N1QL query to a Couchbase database.",
            code = {
                "connectionString: couchbase://localhost",
                "username: couchbase_user",
                "password: couchbase_passwd",
                "query: SELECT * FROM `COUCHBASE_BUCKET`(.`COUCHBASE_SCOPE`.`COUCHBASE_COLLECTION`)",
                "fetchType: FETCH"
            }
        ),
    }
)
public class Query extends CouchbaseConnection implements RunnableTask<Query.Output>, QueryInterface {
    private static final TypeRef<Map<String, Object>> MAP_TYPE_REF = new TypeRef<>() {};

    @NotNull
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);
    protected Object parameters;

    @NotNull
    @NotBlank
    protected String query;

    public Output run(RunContext runContext) throws Exception {
        Cluster session = connect(runContext);

        String renderedQuery = runContext.render(query);
        QueryOptions parametersForQuery = getParametersForQuery();
        QueryResult result = session.query(renderedQuery, parametersForQuery);

        close(session);

        List<Map<String, Object>> rowsAsMap = result.rowsAs(MAP_TYPE_REF);

        Output.OutputBuilder outputBuilder = Output.builder().size((long) rowsAsMap.size());
        return (switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> outputBuilder
                .rows(rowsAsMap);
            case FETCH_ONE -> outputBuilder
                .row(rowsAsMap.stream().findFirst().orElse(null));
            case STORE -> {
                File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                BufferedWriter fileWriter = new BufferedWriter(new FileWriter(tempFile));
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    rowsAsMap.forEach(row -> {
                        try {
                            FileSerde.write(outputStream, row);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    FileSerde.write(outputStream, rowsAsMap);
                }

                fileWriter.flush();
                fileWriter.close();

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
            title = "List containing the fetched data.",
            description = "Only populated if using `FETCH`."
        )
        private List<Map<String, Object>> rows;

        @Schema(
            title = "Map containing the first row of fetched data.",
            description = "Only populated if using `FETCH_ONE`."
        )
        private Map<String, Object> row;

        @Schema(
            title = "The URI of the stored result in Kestra's internal storage.",
            description = "Only populated if using `STORE`."
        )
        private URI uri;

        @Schema(
            title = "The number of rows to be fetched.",
            description = "Only populated if `fetchType` is 'FETCH' or 'STORE'."
        )
        private Long size;
    }
}
