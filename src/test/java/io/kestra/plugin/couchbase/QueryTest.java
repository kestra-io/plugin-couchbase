package io.kestra.plugin.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.kv.UpsertOptions;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class QueryTest extends CouchbaseTest {
    @Inject
    private RunContextFactory runContextFactory;
    @Inject
    private StorageInterface storageInterface;

    @Test
    void simpleQuery_AllTypesParsed() throws Exception {
        RunContext runContext = runContextFactory.of();

        Query query = authentifiedQueryBuilder()
            .query("SELECT * FROM " + BUCKET + " USE KEYS 'a-doc'")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        Query.Output queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(1L));

        Map<String, Object> row = (Map<String, Object>) queryResult.getRow().get(BUCKET);
        assertThat(row.get("c_string"), is("Kestra Doc"));
        assertThat(row.get("c_null"), nullValue());
        assertThat(row.get("c_boolean"), is(true));
        assertThat(row.get("c_int"), is(3));
        assertThat(row.get("c_decimal"), is(3.10));
        assertThat(row.get("c_decimal_e_notation"), is(3000));
        assertThat((Iterable<Number>) row.get("c_number_array"), Matchers.hasItems(3, 3.10, 3000));
        assertThat((Iterable<String>) row.get("c_string_array"), Matchers.hasItems("firstString", "secondString"));

        Map<String, Object> object = (Map<String, Object>) row.get("c_object");
        assertThat(object, aMapWithSize(2));
        assertThat(object.get("c_object_prop"), is("hello"));
        Map<String, Object> subObject = (Map<String, Object>) object.get("c_subobject");
        assertThat(subObject, aMapWithSize(1));
        assertThat(subObject, hasEntry("c_subobject_prop", 5));

        assertThat(row.get("c_date"), is("2006-01-02T15:04:05.567+08:00"));
    }

    @Test
    void simpleQuery_WorksWithScopeAndCollection() throws Exception {
        RunContext runContext = runContextFactory.of();

        Query query = authentifiedQueryBuilder()
            .query("SELECT * FROM " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "` WHERE c_string='A collection doc'")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        Query.Output queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(1L));

        Map<String, Object> row = (Map<String, Object>) queryResult.getRow().get(COLLECTION);
        assertThat(row.get("c_string"), is("A collection doc"));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "?;?;[\"Kestra Doc\",3]",
        "$2;$1;[3,\"Kestra Doc\"]",
        "$string;$int;{\"int\":3, \"string\":\"Kestra Doc\"}"
    }, delimiter = ';')
    void preparedStatement(String firstArg, String secondArg, String parametersJson) throws Exception {
        RunContext runContext = runContextFactory.of();

        Query query = authentifiedQueryBuilder()
            .query("SELECT c_string, c_int FROM " + BUCKET + " WHERE c_string=" + firstArg + " AND c_int=" + secondArg)
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .parameters(JacksonMapper.toObject(parametersJson))
            .build();

        Query.Output queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(1L));

        Map<String, Object> row = queryResult.getRow();
        assertThat(row.get("c_string"), is("Kestra Doc"));
        assertThat(row.get("c_int"), is(3));
    }

    @Test
    void simpleQuery_FetchAll() throws Exception {
        RunContext runContext = runContextFactory.of();

        Query.Output insertQuery = authentifiedQueryBuilder()
            .query("UPSERT INTO " + BUCKET + " (KEY, VALUE) " +
                "VALUES (\"another-doc\", { " +
                "   \"c_string\" : \"Another Kestra Doc\"" +
                "})" +
                "RETURNING *")
            .fetchType(Property.ofValue(FetchType.NONE))
            .build().run(runContext);

        // Only available if adding 'RETURNING *' to insert
        assertThat(insertQuery.getSize(), is(1L));

        Query query = authentifiedQueryBuilder()
            .query("SELECT * FROM " + BUCKET)
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        Query.Output queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(2L));

        List<Map<String, Object>> rows = queryResult.getRows().stream().map(row -> (Map<String, Object>) row.get(BUCKET)).collect(Collectors.toList());
        assertThat(rows, hasSize(2));
        assertThat(rows, Matchers.hasItems(
            hasEntry("c_string", "Kestra Doc"),
            hasEntry("c_string", "Another Kestra Doc")
        ));

        // If we precise field, we get rid of bucket layer in output
        query = authentifiedQueryBuilder()
            .query("SELECT c_string FROM " + BUCKET)
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(2L));

        rows = queryResult.getRows();
        assertThat(rows, hasSize(2));
        assertThat(rows, Matchers.hasItems(
            hasEntry("c_string", "Kestra Doc"),
            hasEntry("c_string", "Another Kestra Doc")
        ));
    }

    @Test
    public void binaryData() throws Exception {
        try (Cluster session = Cluster.connect(CONNECTION_STRING, USER, PASSWORD)) {
            session.waitUntilReady(Duration.ofSeconds(10));
            session.bucket(BUCKET)
                .scope(SCOPE)
                .collection(COLLECTION)
                .upsert(
                    "xml-doc",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><aField>someValue</aField>".getBytes("UTF-8"),
                    UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE)
                );
        }

        Query.Output queryResult = authentifiedQueryBuilder()
            .query("SELECT * FROM " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "` USE KEYS 'xml-doc'")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build().run(runContextFactory.of());

        // We should query bucket through API to be able to decode binary. Not implemented for now.
        assertThat(queryResult.getRow().get(COLLECTION), is("<binary (64 b)>"));
    }

    @Test
    void simpleQuery_ToInternalStorage() throws Exception {
        RunContext runContext = runContextFactory.of();

        Query query = authentifiedQueryBuilder()
            .query("SELECT * FROM " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "` WHERE c_string='A collection doc'")
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        Query.Output queryResult = query.run(runContext);

        assertThat(queryResult.getSize(), is(1L));
    }
}
