package io.kestra.plugin.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.couchbase.models.FetchType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
                        title = "Send a N1QL query to a Couchbase Database",
                        code = {
                                "connectionString: couchbase://localhost",
                                "username: couchbase_user",
                                "password: couchbase_passwd",
                                "query: SELECT * FROM COUCHBASE_BUCKET(.COUCHBASE_SCOPE.COUCHBASE_COLLECTION)",
                                "fetchType: FETCH"
                        }
                ),
        }
)
public class Query extends CouchbaseConnection implements RunnableTask<Query.Output>, QueryInterface {
    @NotNull
    @Builder.Default
    protected FetchType fetchType = FetchType.NONE;
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

        List<Map<String, Object>> rowsAsMap = result.rowsAs(new TypeRef<>() {
        });

        Output.OutputBuilder outputBuilder = Output.builder().size((long) rowsAsMap.size());
        switch (fetchType) {
            case FETCH: {
                return outputBuilder
                        .rows(rowsAsMap)
                        .build();
            }
            case FETCHONE: {
                return outputBuilder
                        .row(rowsAsMap.stream().findFirst().orElse(null))
                        .build();
            }
            case STORE: {
                File tempFile = runContext.tempFile(".ion").toFile();
                BufferedWriter fileWriter = new BufferedWriter(new FileWriter(tempFile));
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    FileSerde.write(outputStream, rowsAsMap);
                }

                fileWriter.flush();
                fileWriter.close();

                return outputBuilder
                        .uri(runContext.putTempFile(tempFile))
                        .build();
            }
        }

        return outputBuilder.build();
    }

    private QueryOptions getParametersForQuery() {
        QueryOptions queryOptions = QueryOptions.queryOptions();

        if (parameters instanceof Map) queryOptions.parameters(JsonObject.from((Map<String, ?>) parameters));
        else if (parameters instanceof List) queryOptions.parameters(JsonArray.from((List<?>) parameters));

        return queryOptions;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
                title = "List containing the fetched data",
                description = "Only populated if using `FETCH`."
        )
        private List<Map<String, Object>> rows;

        @Schema(
                title = "Map containing the first row of fetched data",
                description = "Only populated if using `FETCHONE`."
        )
        private Map<String, Object> row;

        @Schema(
                title = "The uri of the stored result",
                description = "Only populated if using `STORE`"
        )
        private URI uri;

        @Schema(
                title = "The amount of rows fetched"
        )
        private Long size;
    }
}
