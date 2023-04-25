package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface CouchbaseConnectionInterface {
    @Schema(
        title = "Connection string used to locate the Couchbase cluster."
    )
    @PluginProperty(dynamic = true)
    String getConnectionString();

    @Schema(
        title = "Plaintext authentication username"
    )
    @PluginProperty(dynamic = true)
    String getUsername();

    @Schema(
        title = "Plaintext authentication password"
    )
    @PluginProperty(dynamic = true)
    String getPassword();
}
