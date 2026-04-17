# Contributing to NBT Factory

Thank you for your interest in contributing. This document covers the development setup, code style, and pull-request process.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Commit Messages](#commit-messages)
  - [Testing](#testing)
  - [Benchmarks](#benchmarks)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Legal](#legal)

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Git](https://git-scm.com/) | 2.x+ | For cloning and contributing |
| [IntelliJ IDEA](https://www.jetbrains.com/idea/) | Latest | Recommended IDE |

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/minecraft-library/nbt-factory/fork),
   then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/nbt-factory.git
   cd nbt-factory
   ```

2. **Build the project**

   ```bash
   ./gradlew build
   ```

3. **Open in IntelliJ IDEA**

   Open the project root as a Gradle project. Ensure the Lombok plugin is installed and annotation processing is enabled.

## Making Changes

### Branching Strategy

- Create a feature branch from `master`.
- Use a descriptive branch name: `fix/long-array-roundtrip`, `feat/snbt-pretty-print`, `docs/usage-examples`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

- **Annotations** - Use `@NotNull` / `@Nullable` from `org.jetbrains.annotations` on all public method parameters and return types.
- **Lombok** - Use `@Getter`, `@RequiredArgsConstructor`, `@Cleanup`, etc. The logger field (when present) is non-static.
- **Equals/HashCode** - Implement manually using `Objects.equals()` / `Objects.hash()` (not Lombok's `@EqualsAndHashCode`).

#### Javadoc

- **Class level** - Noun phrase describing what the type is.
- **Method level** - Active verb, third person singular.
- **Tags** - `@param`, `@return`, `@throws` on public methods. Lowercase sentence fragments, no trailing period. Single space after param name.
- **Punctuation** - Only single hyphens (` - `) as separators.
- Never use `@author` or `@since`.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Fix LongArrayTag length prefix on empty arrays

The writer emitted a stray zero byte when the array was empty, producing
output that failed to round-trip through any NBT reader.
```

- Use the imperative mood ("Add", "Fix", "Update").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.

### Testing

Tests use JUnit 5 (Jupiter) with Hamcrest matchers.

```bash
./gradlew test                                     # all unit tests
./gradlew test --tests "NbtRoundTripTest.*"        # single class
```

Every new tag type, every codec tweak, and every SNBT/JSON escape change must have a round-trip case in `NbtRoundTripTest` covering:

- the empty value
- the most negative / most positive representable value
- a mid-range realistic value
- if applicable, a value that exercises escape handling (strings, control characters)

### Benchmarks

```bash
./gradlew generateAuctionFixture   # ~40 MB public Hypixel data, no API key
./gradlew jmh
```

Benchmarks live in `src/jmh/java/lib/minecraft/nbt/benchmark/NbtBenchmarks.java`. Add a benchmark when changing any code path that handles large numbers of tags (compound iteration, list encoding, array codecs).

## Submitting a Pull Request

1. Push your branch to your fork.
2. Open a Pull Request against `master` of [minecraft-library/nbt-factory](https://github.com/minecraft-library/nbt-factory).
3. In the PR description include:
   - Summary of the change and the motivation.
   - Steps to verify (which test(s), which benchmark(s) if perf-relevant).
   - Any API surface changes.

### What gets reviewed

- Round-trip correctness for every tag type touched.
- Public API stability - breaking changes need to be flagged and discussed.
- Allocation / performance impact for hot paths (codec, compound iteration).
- Test coverage for the new behavior.

## Reporting Issues

Use [GitHub Issues](https://github.com/minecraft-library/nbt-factory/issues) to report bugs or request features.

When reporting a bug include:

- **Java version** (`java --version`)
- **Operating system**
- **A minimal reproducer** - NBT bytes (base64 or SNBT) plus the code that fails
- **Full error stacktrace**
- **Expected vs. actual behavior**

## Legal

By submitting a pull request, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE.md), the same license that covers this project.
