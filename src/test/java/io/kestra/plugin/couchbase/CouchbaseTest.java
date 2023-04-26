package io.kestra.plugin.couchbase;

public class CouchbaseTest {
    protected static final String USER = "Administrator";
    protected static final String PASSWORD = "password";
    protected static final String BUCKET = "kestra";
    protected static final String SCOPE = "some-scope";
    protected static final String COLLECTION = "some-collection";
    protected static final String CONNECTION_STRING = "couchbase://127.0.0.1";

    protected Query.QueryBuilder authentifiedQueryBuilder() {
        return Query.builder()
            .connectionString(CONNECTION_STRING)
            .username(USER)
            .password(PASSWORD);
    }
}
