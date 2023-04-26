FROM couchbase/server:latest

COPY configure-couchbase.sh /opt/couchbase
COPY src/test/resources/init.sql /opt/couchbase

CMD ["couchbase-server"]