package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.plugin.couchbase.models.FetchType;
import io.swagger.v3.oas.annotations.media.Schema;

public interface QueryInterface {
    @Schema(
        title = "N1QL query to execute."
    )
    @PluginProperty(dynamic = true)
    String getQuery();

    @Schema(
        title = "Parameters in case of positional or named parameters within query.",
        description = "See Couchbase documentation about Prepared Statements for query syntax." +
            "This should be supplied with a parameter map if using named parameters or an array for positional ones",
        example = "my-field: my-value\n" +
            "my-second-field: another-value\n" +
            "or\n" +
            "- my-value\n" +
            "- another-value"
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    Object getParameters();

    @Schema(
        title = "The way you want to store the data",
        description = "FETCHONE output the first row\n"
            + "FETCH output all the row\n"
            + "STORE store all row in a file\n"
            + "NONE do nothing"
    )
    @PluginProperty
    FetchType getFetchType();
}
