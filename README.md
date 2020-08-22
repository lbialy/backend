# branchtalk

Demo of how scalable implementation of Reddit-like service could be made
in Scala to demonstrate some patterns and principles.

## Status

Work-in-progress, see [TODO](TODO.md)

## Development

Building and testing requires java installed. Then

```bash
./sbt
```

downloads and runs sbt shell.

To run integration tests or local env you need docker and make:

```bash
make dev-bg   # starts services in background
make dev-up   # starts services in terminal (I suggest a separate tab/window)
```

Then it is possible to run it tests:

```bash
sbt> it:test
```

```bash
make dev-down # shuts down services
```
