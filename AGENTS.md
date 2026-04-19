# Kestra Couchbase Plugin

## What

- Provides plugin components under `io.kestra.plugin.couchbase`.
- Includes classes such as `CouchbaseConnection`, `Trigger`, `Query`.

## Why

- What user problem does this solve? Teams need to execute Couchbase N1QL queries and trigger Kestra flows from query results from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Couchbase steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Couchbase.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `couchbase`

Infrastructure dependencies (Docker Compose services):

- `couchbase`

### Key Plugin Classes

- `io.kestra.plugin.couchbase.Query`
- `io.kestra.plugin.couchbase.Trigger`

### Project Structure

```
plugin-couchbase/
├── src/main/java/io/kestra/plugin/couchbase/
├── src/test/java/io/kestra/plugin/couchbase/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
