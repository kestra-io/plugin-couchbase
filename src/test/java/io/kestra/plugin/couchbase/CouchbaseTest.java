package io.kestra.plugin.couchbase;

import com.github.dockerjava.api.model.ContainerNetwork;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

import java.io.IOException;

public class CouchbaseTest {
    protected static String USER;
    protected static String PASSWORD;
    protected static final String BUCKET = "kestra";
    protected static final String SCOPE = "some-scope";
    protected static final String COLLECTION = "some-collection";

    protected final static CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:latest")
        .withBucket(new BucketDefinition(BUCKET));

    @BeforeAll
    static void startCouchbase() throws IOException, InterruptedException {
        couchbaseContainer.start();
        USER = couchbaseContainer.getUsername();
        PASSWORD = couchbaseContainer.getPassword();

        String internalContainerIp = couchbaseContainer.getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .values()
            .stream()
            .findFirst()
            .map(ContainerNetwork::getIpAddress).orElse(couchbaseContainer.getHost());
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "INSERT INTO " + BUCKET + " (KEY, VALUE) " +
                "VALUES (\"a-doc\", { " +
                "\"c_string\" : \"Kestra Doc\"," +
                "\"c_null\": NULL," +
                "\"c_boolean\": TRUE," +
                "\"c_int\": 3," +
                "\"c_decimal\": 3.10," +
                "\"c_decimal_e_notation\": 3E3," +
                "\"c_number_array\": [3, 3.10, 3E3]," +
                "\"c_string_array\": [\"firstString\", \"secondString\"]," +
                "\"c_object\":{" +
                "   \"c_object_prop\": \"hello\"," +
                "   \"c_subobject\": {" +
                "       \"c_subobject_prop\": 5" +
                "   }" +
                "}," +
                "\"c_date\": \"2006-01-02T15:04:05.567+08:00\"" +
                "})");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "CREATE PRIMARY INDEX ON " + BUCKET);
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "CREATE SCOPE " + BUCKET + ".`" + SCOPE + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "CREATE COLLECTION " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "CREATE PRIMARY INDEX ON " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER + ":" + PASSWORD,
            "-s", "INSERT INTO " + BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "` (KEY, VALUE) " +
                "VALUES (\"a-scoped-collection-doc\", { " +
                "\"c_string\" : \"A collection doc\"" +
                "})");
    }

    @AfterAll
    static void stopCouchbase(){
        couchbaseContainer.stop();
    }

    protected Query.QueryBuilder authentifiedQueryBuilder() {
        return Query.builder()
            .connectionString(couchbaseContainer.getConnectionString())
            .username(USER)
            .password(PASSWORD);
    }
}
