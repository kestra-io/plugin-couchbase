#!/bin/bash

set -m

bash -c 'while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' localhost:8091/pools)" != "200" ]]; do sleep 1; done'

couchbase-cli cluster-init -c localhost \
--cluster-username Administrator \
--cluster-password password \
--services data,query,index \
--cluster-ramsize 512 \
--cluster-index-ramsize 256

couchbase-cli bucket-create -c localhost \
-u Administrator \
-p password \
--bucket kestra \
--bucket-type couchbase \
--bucket-ramsize 100

couchbase-cli collection-manage -c localhost \
-u Administrator \
-p password \
--bucket kestra \
--create-scope some-scope

couchbase-cli collection-manage -c localhost \
-u Administrator \
-p password \
--bucket kestra \
--create-collection some-scope.some-collection

bash -c 'while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' localhost:8093/query)" != "400" ]]; do sleep 1; done'

cbq -e localhost:8093 -c Administrator:password -f "$1"