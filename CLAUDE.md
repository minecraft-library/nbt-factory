# CLAUDE.md

Guidance for Claude Code working in this repo. Assumes the global `~/.claude/CLAUDE.md` (Javadoc style, exception style, control flow, git policy) has been loaded.

## Build & Test

```bash
./gradlew build                                    # compile + test
./gradlew test                                     # unit tests only
./gradlew test --tests "NbtRoundTripTest.*"        # single test class
./gradlew generateAuctionFixture                   # fetch ~40MB JMH corpus from Hypixel (no API key)
./gradlew jmh                                      # run benchmarks (after fixture exists)
```

## Benchmarking

The JMH suite exercises three corpora: the synthetic compound, the live Hypixel auction
fixture (`auctions.bin`, ~40 MB, gitignored), and the vendored simdnbt parity corpus
(`src/test/resources/simdnbt-corpus/`, ~27 KB, checked in). G1 is the only wired profile:

```bash
./gradlew jmh -PjmhInclude=SimdNbtParity
```

JMH always runs with `-XX:MaxInlineLevel=20 -XX:FreqInlineSize=500 -XX:+UseG1GC` and
writes JSON to `build/results/jmh/results.json` (the JMH plugin's default).

Render a simdnbt-style throughput table from one or more JSON files:

```bash
python3 tools/jmh-report.py build/results/jmh/results.json
```

The simdnbt parity corpus is vendored from `https://git.matdoes.dev/mat/simdnbt`
(MIT license, pinned at `master @ 4cc67bcd980c`, tagged 0.10.0). To refresh it:

```bash
bash tools/fetch-simdnbt-corpus.sh
```

The script verifies SHA-256 hashes; CI never runs it.

## Architecture

`NbtFactory` is the single public entry point. All methods are static (`@UtilityClass`). It dispatches to format-specific codecs under `io/`.

| Package | Role |
|---------|------|
| `lib.minecraft.nbt` | `NbtFactory` - all public read/write methods |
| `lib.minecraft.nbt.exception` | `NbtException`, `NbtMaxDepthException` |
| `lib.minecraft.nbt.tags` | `Tag`, `TagType` enum (dispatch) |
| `lib.minecraft.nbt.tags.primitive` | `ByteTag`..`DoubleTag`, `StringTag`, `EndTag`, `NumericalTag` (shared base) |
| `lib.minecraft.nbt.tags.array` | `ByteArrayTag`, `IntArrayTag`, `LongArrayTag` |
| `lib.minecraft.nbt.tags.collection` | `CompoundTag` (map), `ListTag` (homogeneous list) |
| `lib.minecraft.nbt.io` | `NbtInput`/`NbtOutput` dispatch, `NbtByteCodec` contract, `NbtModifiedUtf8`, `NbtKnownKeys` |
| `lib.minecraft.nbt.io.buffer` | heap-backed `NbtInputBuffer` / `NbtOutputBuffer` |
| `lib.minecraft.nbt.io.stream` | `DataInputStream` / `DataOutputStream` wrappers |
| `lib.minecraft.nbt.io.snbt` | stringified-NBT serializer + deserializer |
| `lib.minecraft.nbt.io.json` | JSON serializer + deserializer |
| `lib.minecraft.nbt.borrow` | zero-allocation read-only navigator API (tape + retained buffer) |

## Borrow API

`@ApiStatus.Experimental`. Zero-allocation read path: parses input bytes into a flat
`long[]` tape and returns navigators that decode lazily on field access. Wins come
from skipping decode of fields the caller never touches, not from full zero-copy.

When to use: read-heavy code that touches a small subset of fields per pass (price
checks, search filters, key probes). Don't use when you'll read every field - the
materializing path is roughly equal there.

```java
BorrowedCompoundTag bc = NbtFactory.borrowFromByteArray(payload);
BorrowedTag<?> display = bc.get("display");
// payload bytes retained until bc is GC'd - do not mutate them.
CompoundTag detached = bc.materialize(); // escape hatch; no buffer retention
```

Buffer-retention rule: the returned `BorrowedCompoundTag` holds a strong reference
to the (possibly decompressed) input bytes. Pin lifetime to the borrow; do not mutate
the input array.

Performance ballpark from Phase C6's `BorrowVsMaterializeBenchmark`: ~2.26x on
`complex_player.dat`, 2-3x on compound- and string-heavy NBT, 1.2-1.5x on
primitive-array-heavy NBT. See `lib.minecraft.nbt.borrow.package-info` for the full
contract.

## Dependencies

Three externals. Resist adding more.

| Dep | Used by |
|-----|---------|
| `com.google.code.gson:gson` | `io/json/`, `io/snbt/StringTag` escape table, `AuctionFixtureGenerator` |
| `com.github.simplified-dev:utils` | `dev.simplified.stream.Compression` (gzip), `dev.simplified.util.StringUtil` (base64) |
| `org.jetbrains:annotations` | `@NotNull`, `@Nullable`, `@PrintFormat` |

No Spring, no JPA, no Hibernate, no Feign, no service locator. This is a pure library.

## Conventions

- `NbtFactory` methods throw `NbtException` (unchecked). Wrap every `IOException` from the stack.
- `CompoundTag`/`ListTag` are mutable; primitive/array tags are effectively immutable.
- Tests in `src/test` are JUnit 5; benchmarks in `src/jmh` use the `me.champeau.jmh` plugin (auto-registered source set).
- The `auctions.bin` fixture is gitignored and regenerated on demand via `generateAuctionFixture`.

## When adding a new tag type

1. Create the `Tag` subclass under the appropriate `tags/` subpackage.
2. Add a `TagType` enum constant with its id and codec.
3. Implement `NbtByteCodec<T>` for buffer I/O.
4. Add SNBT + JSON serializer cases (`SnbtSerializer`, `NbtJsonSerializer`) and their deserializer mirrors.
5. Add a round-trip case in `NbtRoundTripTest` covering edge values.
