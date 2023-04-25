package io.kestra.plugin.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.env.ClusterEnvironment;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
    protected String username;

    @NotNull
    @NotBlank
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
        cluster.close();
        cluster.environment().close();
    }
}
