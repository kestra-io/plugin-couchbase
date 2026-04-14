package io.kestra.plugin.couchbase;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class TriggerTest extends CouchbaseTest {

    @SuppressWarnings("unchecked")
    @Test
    @EvaluateTrigger(flow = "flows/couchbase-listen.yml", triggerId = "watch")
    void simpleQueryTrigger(Optional<Execution> optionalExecution) {
        assertThat(optionalExecution.isPresent(), is(true));
        Execution execution = optionalExecution.get();
        Map<String, Object> row = (Map<String, Object>) ((Map<String, Object>) execution.getTrigger().getVariables().get("row")).get(COLLECTION);
        assertThat(row.get("c_string"), is("A collection doc"));
    }
}
