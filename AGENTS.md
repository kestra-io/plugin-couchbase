# Kestra Couchbase Plugin

## What

- Provides plugin components under `io.kestra.plugin.couchbase`.
- Includes classes such as `CouchbaseConnection`, `Trigger`, `Query`.

## Why

- This plugin integrates Kestra with Couchbase.
- It provides tasks that execute Couchbase N1QL queries and trigger Kestra flows from query results.

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
