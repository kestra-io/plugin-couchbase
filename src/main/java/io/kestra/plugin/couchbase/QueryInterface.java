package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface QueryInterface {
    @Schema(
        title = "Renderable N1QL statement to run",
        description = "Rendered with flow variables before execution against Couchbase. Ensure bucket, scope, and collection references are accessible to the provided credentials."
    )
    @PluginProperty(dynamic = true)
    String getQuery();

    @Schema(
        title = "Query parameters for placeholders",
        description = "Renderable values for named or positional parameters. Use a map for named parameters or a list/array for positional ones; see Couchbase prepared statement syntax for details.",
        example = "my-field: my-value\n" +
            "my-second-field: another-value\n" +
            "or\n" +
            "- my-value\n" +
            "- another-value",
        anyOf = {
            Map.class,
            String[].class
        }
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    Object getParameters();

    @Schema(
        title = "How to return or store query results",
        description = "Defaults to STORE, which writes all rows to Kestra internal storage. FETCH returns all rows inline, FETCH_ONE returns the first row, and NONE skips output."
    )
    Property<FetchType> getFetchType();
}
