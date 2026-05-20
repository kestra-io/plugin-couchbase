# How to use the Couchbase plugin

Run N1QL queries and poll for results in Couchbase from Kestra flows.

## Authentication

Set `connectionString` to your Couchbase connection string (e.g. `couchbase://localhost`), `username`, and `password`. Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`Query` runs a N1QL statement set in `query`. Pass named or positional `parameters` as a map or list. Control result handling with `fetchType`: `STORE` (default, writes to internal storage), `FETCH` returns all rows, `FETCH_ONE` returns the first row, `NONE` discards results.

`Trigger` polls Couchbase on a schedule (default 60 seconds) and starts one execution per batch of matching rows. Set `query`, `parameters`, and `fetchType` the same way as the `Query` task.
