# Examples

Standalone sample applications using the `quarkus-graalpy` extension.

| Example | What it shows |
| --- | --- |
| [classifier](classifier/) | REST endpoint that calls a Python `predict` function through a declarative `@GraalPyModuleBinding`. |

Each example is a self-contained Maven project. From the repository root:

```bash
mvn -pl examples/classifier quarkus:dev
```

> **Note:** examples depend on `io.quarkiverse.graalpy:quarkus-graalpy:0.1.0-SNAPSHOT`. Run `mvn clean install` from the repository root once before building an example so the snapshot is available in your local Maven repository.
