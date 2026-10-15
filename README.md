# Quarkus GraalPy

Quarkus extension that embeds [GraalPy](https://www.graalvm.org/python/) in Java apps. Exposes a managed `Context`, a fluent API, and declarative Java-to-Python bindings generated at build time.

Based on [quarkusio/quarkus#56029](https://github.com/quarkusio/quarkus/issues/56029).

## Features

- Managed `GraalPy` CDI bean (`@ApplicationScoped`), lifecycle tied to the app.
- `src/main/python/` is auto-packaged into the runtime classpath and added to `sys.path`.
- `@GraalPyModuleBinding("mymodule")` on a Java interface: a CDI implementation is generated at build time (Jandex + Gizmo) that routes calls to Python functions.
- Fluent API: `graalPy.module("x").function("y").invoke(args).as(Type.class)`.
- Build-time validation (`quarkus.graalpy.validate-bindings=true`): fails the build if a `@GraalPyModuleBinding` points at a missing module or function.
- Dev Mode: edits under `src/main/python/` trigger a reload.
- Test Python sources under `src/test/python/`, loaded only in `test` mode.

## Modules

- [`runtime/`](runtime/): public API (`@GraalPyModuleBinding`, `@GraalPyFunction`, `GraalPy` bean, `GraalPyConfig`).
- [`deployment/`](deployment/): build-time processor (discovery, Gizmo codegen, source packaging, dev-mode watching, validation).
- [`integration-tests/`](integration-tests/): in-process tests plus a REST endpoint sample.
- [`docs/`](docs/): Antora docs ([index.adoc](docs/modules/ROOT/pages/index.adoc)).

## Requirements

- JDK 17+
- Maven 3.9+
- Quarkus 3.27.0
- GraalPy 25.0.0

## Build

```bash
mvn clean install
```

## Quick start

Add the extension to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.graalpy</groupId>
    <artifactId>quarkus-graalpy</artifactId>
    <version>999-SNAPSHOT</version>
</dependency>
```

Drop a Python module under `src/main/python/`:

```python
# src/main/python/classifier.py
from types import SimpleNamespace


def predict(features):
    total = sum(features)
    label = "positive" if total >= 0 else "negative"
    return SimpleNamespace(label=label, confidence=min(1.0, abs(total)))
```

Declare a typed binding:

```java
@GraalPyModuleBinding("classifier")
public interface Classifier {
    Prediction predict(float[] features);
}
```

Inject it like any CDI bean:

```java
@Inject
Classifier classifier;

Prediction p = classifier.predict(new float[]{0.2f, -0.1f, 0.9f});
```

Or use the programmatic API:

```java
@Inject
GraalPy graalPy;

Prediction p = graalPy.module("classifier")
        .function("predict")
        .invoke(features)
        .as(Prediction.class);
```

A full runnable sample lives in [integration-tests/](integration-tests/).

## Configuration

| Property | Default | Description |
| --- | --- | --- |
| `quarkus.graalpy.source-dir` | `src/main/python` | Directory with application Python sources. |
| `quarkus.graalpy.test-source-dir` | `src/test/python` | Directory with test Python sources (test mode only). |
| `quarkus.graalpy.include-test-sources` | `true` | Include test sources when running tests. |
| `quarkus.graalpy.validate-bindings` | `false` | Fail the build on missing modules/functions. |
| `quarkus.graalpy.source-path` | _unset_ | Filesystem path prepended to `sys.path` at runtime. |

Recommended for dev:

```properties
%dev.quarkus.graalpy.validate-bindings=true
```

To load Python sources from a directory outside the project (on top of packaged ones):

```properties
%dev.quarkus.graalpy.source-path=/home/me/python-scripts
```

## Documentation

Full docs live under [docs/](docs/) as an Antora module. Start from [docs/modules/ROOT/pages/index.adoc](docs/modules/ROOT/pages/index.adoc).

Topics:

- Getting started
- Programmatic API
- Declarative bindings (`@GraalPyModuleBinding`, `@GraalPyFunction`)
- Build-time validation
- Dev Mode
- Test Python sources
- Configuration reference
- Testing with `@QuarkusTest`
- Current limitations

## Roadmap

Items from the upstream proposal not yet implemented:

- Native image support (plus docs on C extension / wheel limitations).
- Full integration with `python-embedding` for PyPI deps (`requirements.txt`, venv via `graalpy-maven-plugin`).
- Typed `GraalPyException` hierarchy for import/execution failures (currently `PolyglotException`).
- Extended type conversions beyond GraalPy's default `Value.as(Class)` (e.g. `BigDecimal`, `LocalDate`, custom collection generics).

PRs welcome. See [the upstream proposal](https://github.com/quarkusio/quarkus/issues/56029) for the full roadmap.
