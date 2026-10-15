# Contributing

Thanks for your interest in contributing to Quarkus GraalPy.

## Building

```bash
./mvnw clean install
```

## Running integration tests

```bash
./mvnw -pl integration-tests verify
```

## Dev mode

```bash
./mvnw -pl integration-tests quarkus:dev
```

## Reporting issues

Open an issue at https://github.com/quarkiverse/quarkus-graalpy/issues.

## Pull requests

- Fork the repo and create a feature branch.
- Keep PRs focused and reasonably small.
- Add a test for anything non-trivial.
- Make sure `./mvnw clean install` passes locally.
