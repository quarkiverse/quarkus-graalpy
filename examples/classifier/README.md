# Classifier example

Minimal Quarkus app that calls a Python function through a declarative `@GraalPyModuleBinding` interface and exposes it as a REST endpoint.

## Layout

```
examples/classifier/
├── pom.xml
└── src/main/
    ├── java/org/acme/
    │   ├── Classifier.java              # @GraalPyModuleBinding interface, impl generated at build time
    │   └── ClassifierResource.java      # REST endpoint, injects Classifier
    ├── python/classifier.py             # Python module
    └── resources/application.properties # dev-mode binding validation
```

## Prerequisites

Install the extension snapshot into your local Maven repository once:

```bash
mvn -N install
mvn -pl runtime,deployment install
```

## Run

```bash
mvn -pl examples/classifier quarkus:dev
```

Then:

```bash
curl -X POST http://localhost:8080/classify \
  -H 'Content-Type: application/json' \
  -d '[0.2, -0.1, 0.9]'
# {"label":"positive","confidence":1.0}
```

Edit `src/main/python/classifier.py` — Quarkus reloads the Python context on the next request.

## How it works

[`Classifier`](src/main/java/org/acme/Classifier.java) declares the binding:

```java
@GraalPyModuleBinding("classifier")
public interface Classifier {
    Prediction predict(float[] features);
}
```

At build time the extension scans this interface, generates `Classifier_GraalPyImpl` with Gizmo, and registers it as an `@ApplicationScoped` CDI bean. [`ClassifierResource`](src/main/java/org/acme/ClassifierResource.java) injects it like any other bean:

```java
@Inject
Classifier classifier;
```

`Prediction` is returned as a Polyglot proxy over the Python `SimpleNamespace`; the resource wraps it in a plain record so Jackson can serialize the response.

With `%dev.quarkus.graalpy.validate-bindings=true` the build fails fast in dev mode if `classifier.py` is missing or lacks the `predict` function.

## Loading Python sources from a custom path

If you'd rather keep `classifier.py` outside the project (e.g. in a shared scripts directory), set `quarkus.graalpy.source-path` to that directory and it will be prepended to `sys.path` on top of the packaged sources:

```properties
%dev.quarkus.graalpy.source-path=/home/me/python-scripts
```
