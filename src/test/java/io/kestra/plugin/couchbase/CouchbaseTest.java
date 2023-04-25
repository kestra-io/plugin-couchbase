package io.kestra.plugin.couchbase;

import com.github.dockerjava.api.model.ContainerNetwork;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

public class CouchbaseTest {
    protected static final String USER_PASSWD_BUCKET = "kestra";
    protected static final String SCOPE = "some-scope";
    protected static final String COLLECTION = "some-collection";

    protected final static CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:latest")
        .withCredentials(USER_PASSWD_BUCKET, USER_PASSWD_BUCKET)
        .withBucket(new BucketDefinition(USER_PASSWD_BUCKET));

    @BeforeAll
    static void startCouchbase() throws IOException, InterruptedException {
        couchbaseContainer.start();

        String internalContainerIp = couchbaseContainer.getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .values()
            .stream()
            .findFirst()
            .map(ContainerNetwork::getIpAddress).orElse(couchbaseContainer.getHost());
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER_PASSWD_BUCKET + ":" + USER_PASSWD_BUCKET,
            "-s", "INSERT INTO " + USER_PASSWD_BUCKET + " (KEY, VALUE) " +
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
            "-c", USER_PASSWD_BUCKET + ":" + USER_PASSWD_BUCKET,
            "-s", "CREATE SCOPE " + USER_PASSWD_BUCKET + ".`" + SCOPE + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER_PASSWD_BUCKET + ":" + USER_PASSWD_BUCKET,
            "-s", "CREATE COLLECTION " + USER_PASSWD_BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER_PASSWD_BUCKET + ":" + USER_PASSWD_BUCKET,
            "-s", "CREATE PRIMARY INDEX ON " + USER_PASSWD_BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "`");
        couchbaseContainer.execInContainer("/opt/couchbase/bin/cbq",
            "-e", internalContainerIp,
            "-c", USER_PASSWD_BUCKET + ":" + USER_PASSWD_BUCKET,
            "-s", "INSERT INTO " + USER_PASSWD_BUCKET + ".`" + SCOPE + "`.`" + COLLECTION + "` (KEY, VALUE) " +
                "VALUES (\"a-scoped-collection-doc\", { " +
                "\"c_string\" : \"A collection doc\"" +
                "})");
    }

    protected Query.QueryBuilder authentifiedQueryBuilder() {
        return Query.builder()
            .connectionString(couchbaseContainer.getConnectionString())
            .username(USER_PASSWD_BUCKET)
            .password(USER_PASSWD_BUCKET);
    }
}
