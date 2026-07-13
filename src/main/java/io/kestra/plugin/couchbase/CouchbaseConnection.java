package io.kestra.plugin.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.env.ClusterEnvironment;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.kestra.core.models.annotations.PluginProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class CouchbaseConnection extends Task implements CouchbaseConnectionInterface {
    @NotNull
    @NotBlank
    protected String connectionString;

    @NotNull
    @NotBlank
    @PluginProperty(secret = true, dynamic = true, group = "connection")
    protected String username;

    @NotNull
    @NotBlank
    @ToString.Exclude
    @PluginProperty(secret = true, dynamic = true, group = "connection")
    protected String password;

    protected Cluster connect(RunContext runContext) throws IllegalVariableEvaluationException {
        return Cluster.connect(
            runContext.render(connectionString),
            authenticationOptions(runContext)
        );
    }

    private ClusterOptions authenticationOptions(RunContext runContext) throws IllegalVariableEvaluationException {
        return ClusterOptions.clusterOptions(runContext.render(username), runContext.render(password)).environment(ClusterEnvironment.create());
    }

    protected void close(Cluster cluster) {
        cluster.disconnect();
        cluster.environment().shutdown();
    }
}
