# Quarkus GraalPy

Quarkus extension that embeds [GraalPy](https://www.graalvm.org/python/) in Java applications and exposes it through idiomatic Quarkus APIs — a managed `Context`, a fluent programmatic API, and declarative Java-to-Python bindings generated at build time.

Inspired by the proposal in [quarkusio/quarkus#56029](https://github.com/quarkusio/quarkus/issues/56029).

## What it does

- **Managed `GraalPy` context** — single `@ApplicationScoped` CDI bean, lifecycle tied to the Quarkus app.
- **Python source packaging** — `src/main/python/` is auto-packaged into the runtime classpath and placed on `sys.path`. No manual `sys.path` juggling.
- **Declarative bindings** — `@GraalPyModuleBinding("mymodule")` on a Java interface produces an `@ApplicationScoped` implementation at build time (Jandex + Gizmo) that marshals Java calls to Python functions.
- **Fluent programmatic API** — `graalPy.module("x").function("y").invoke(args).as(Type.class)` for ad-hoc calls.
- **Build-time validation** — `quarkus.graalpy.validate-bindings=true` opens a GraalPy context during augmentation and fails the build if any `@GraalPyModuleBinding` points at a missing module or function.
- **Dev Mode** — edits/adds/removes under `src/main/python/` trigger a live reload; the Python context is recycled transparently.
- **Test sources** — `src/test/python/` is a parallel source root for Python fixtures used only in `test` launch mode.

## Modules

- [`runtime/`](runtime/) — public API: `@GraalPyModuleBinding`, `@GraalPyFunction`, the CDI-managed `GraalPy` bean, `GraalPyConfig`.
- [`deployment/`](deployment/) — build-time processor: discovery, Gizmo codegen, source packaging, dev-mode watching, build-time binding validation.
- [`integration-tests/`](integration-tests/) — in-process tests exercising both the declarative binding and `src/test/python` loading.
- [`docs/`](docs/) — Antora documentation module ([index.adoc](docs/modules/ROOT/pages/index.adoc)).
- [`examples/`](examples/) — standalone sample applications.

## Requirements

- JDK 17+
- Maven 3.9+
- Quarkus 3.27.0 (parent `pom.xml`)
- GraalPy 25.0.0 (parent `pom.xml`)

## Build

```bash
mvn clean install
```

## Quick start

Add the extension in your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.graalpy</groupId>
    <artifactId>quarkus-graalpy</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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

Declare a typed binding in Java:

```java
@GraalPyModuleBinding("classifier")
public interface Classifier {
    Prediction predict(float[] features);
}
```

Inject it like any CDI bean — the implementation is generated at build time:

```java
@Inject
Classifier classifier;

Prediction p = classifier.predict(new float[]{0.2f, -0.1f, 0.9f});
```

Or, for ad-hoc calls, use the programmatic API:

```java
@Inject
GraalPy graalPy;

Prediction p = graalPy.module("classifier")
        .function("predict")
        .invoke(features)
        .as(Prediction.class);
```

A full runnable example lives in [examples/classifier/](examples/classifier/).

## Configuration

| Property | Default | Description |
| --- | --- | --- |
| `quarkus.graalpy.source-dir` | `src/main/python` | Project-relative directory holding application Python sources, packaged into classpath resources at build time. |
| `quarkus.graalpy.test-source-dir` | `src/test/python` | Project-relative directory holding test Python sources, packaged in `test` launch mode only. |
| `quarkus.graalpy.include-test-sources` | `true` | Whether to include `test-source-dir` when running tests. |
| `quarkus.graalpy.validate-bindings` | `false` | When `true`, the build fails if any `@GraalPyModuleBinding` references a missing module/function. Recommended under `%dev.`. |
| `quarkus.graalpy.source-path` | _unset_ | Filesystem path prepended to `sys.path` at runtime (in addition to packaged sources). Useful in dev mode. |

Recommended development profile:

```properties
%dev.quarkus.graalpy.validate-bindings=true
```

To load Python modules from an arbitrary directory on disk — on top of the packaged `src/main/python/` sources — point `source-path` at it. The directory is prepended to `sys.path`, so modules there win over packaged ones with the same name:

```properties
%dev.quarkus.graalpy.source-path=/home/me/python-scripts
```

## Documentation

Full reference docs live under [docs/](docs/) as an Antora module. Start from [docs/modules/ROOT/pages/index.adoc](docs/modules/ROOT/pages/index.adoc). Topics covered:

- Getting started
- Programmatic API
- Declarative bindings (`@GraalPyModuleBinding`, `@GraalPyFunction`)
- Build-time validation
- Dev Mode
- Test Python sources
- Configuration reference
- Testing with `@QuarkusTest`
- Current limitations

## Examples

| Example | What it shows |
| --- | --- |
| [classifier](examples/classifier/) | REST endpoint that invokes a Python `predict` function through a declarative `@GraalPyModuleBinding`. |

## Roadmap

Items from the upstream proposal that are **not yet implemented**:

- Native image support (and documentation of C extension / wheel limitations).
- Full integration with `python-embedding` for PyPI dependency management (`requirements.txt`, venv handling via `graalpy-maven-plugin`).
- Typed `GraalPyException` hierarchy for import / execution failures (currently surfaces as `PolyglotException`).
- Extended type conversions beyond what GraalPy's default `Value.as(Class)` covers (e.g. `BigDecimal`, `LocalDate`, custom collection generics).

Contributions welcome — see [the upstream proposal](https://github.com/quarkusio/quarkus/issues/56029) for the full roadmap.
