This plugin runs N1QL queries against a Couchbase cluster and lets a flow react to query results.

## Tasks

- `Query` executes a N1QL statement and captures the result. Set `fetchType` to:
  - `FETCH` to return all rows inline in the task output,
  - `FETCH_ONE` to return only the first row,
  - `STORE` to write the full result set to Kestra internal storage as an ion file.

## Triggers

- `Trigger` polls a N1QL query on an interval and starts an execution when it returns rows.

## Connection

Connect with a `connectionString` (for example `couchbase://localhost`), a `username`, and a `password`.

Provide the password and any other credentials through `{{ secret('...') }}` rather than inline values.
