FROM couchbase/server:latest

COPY --chown=couchbase:couchbase configure-couchbase.sh /opt/couchbase
COPY --chown=couchbase:couchbase src/test/resources/init.sql /opt/couchbase

USER couchbase

CMD ["couchbase-server"]
