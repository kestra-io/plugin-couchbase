package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.common.FetchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface QueryInterface {
    @Schema(
        title = "N1QL query to execute on Couchbase database."
    )
    @PluginProperty(dynamic = true)
    String getQuery();

    @Schema(
        title = "Query parameters, can be positional or named parameters.",
        description = "See Couchbase documentation about Prepared Statements for query syntax. " +
            "This should be supplied with a parameter map if using named parameters, or an array for positional ones.",
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
        title = "The way you want to fetch and/or store the data.",
        description = "FETCH_ONE - output just the first row.\n"
            + "FETCH - output all the rows.\n"
            + "STORE - store all the rows in a file.\n"
            + "NONE - do nothing."
    )
    @PluginProperty
    FetchType getFetchType();
}
