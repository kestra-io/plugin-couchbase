package io.kestra.plugin.couchbase;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface CouchbaseConnectionInterface {
    @Schema(
        title = "Couchbase connection string",
        description = "Full connection string (e.g., couchbase://host) rendered with flow variables before opening the cluster."
    )
    @PluginProperty(dynamic = true)
    String getConnectionString();

    @Schema(
        title = "Cluster username",
        description = "Renderable username for authenticating to the cluster; store sensitive values in secrets."
    )
    @PluginProperty(dynamic = true)
    String getUsername();

    @Schema(
        title = "Cluster password",
        description = "Renderable password for authenticating to the cluster; prefer secret storage."
    )
    @PluginProperty(dynamic = true)
    String getPassword();
}
