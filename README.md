# NBT Factory

A fast, dependency-light Minecraft NBT (Named Binary Tag) reader/writer for Java 21. Handles every tag type across the binary, gzip-compressed, SNBT, JSON, and base64 wire formats.

> [!IMPORTANT]
> The NBT binary format is a specification designed by [Mojang AB](https://www.minecraft.net/) (a Microsoft subsidiary). This library is a clean-room implementation of that specification - no Mojang code or assets are bundled. You are responsible for ensuring your use of NBT data complies with the [Minecraft EULA](https://www.minecraft.net/en-us/eula) and [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines).

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Usage](#usage)
  - [IntelliJ IDEA](#intellij-idea)
- [How It Works](#how-it-works)
- [Benchmarks](#benchmarks)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Every tag type** - `ByteTag`, `ShortTag`, `IntTag`, `LongTag`, `FloatTag`, `DoubleTag`, `StringTag`, `ByteArrayTag`, `IntArrayTag`, `LongArrayTag`, `ListTag`, `CompoundTag`, plus the sentinel `EndTag`
- **Every wire format** - raw binary, gzip-compressed binary, SNBT (stringified NBT), JSON, and base64
- **Every I/O surface** - `byte[]`, `File`, `InputStream`/`OutputStream`, `URL`, and in-memory buffers
- **Round-trip tested** - exhaustive `NbtRoundTripTest` covers primitives at edge values, arrays (empty + large), deep nesting, and heterogeneous lists
- **JMH-benchmarked** - synthetic corpus and real Hypixel SkyBlock auction data exercised by `NbtBenchmarks`
- **Three dependencies** - Gson, Lombok, and `simplified-dev/utils` - nothing else

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | 9.4+ | Wrapper included |
| [Git](https://git-scm.com/) | 2.x+ | For cloning the repository |

### Installation

Add JitPack and the dependency to your build:

<details>
<summary>Gradle (Kotlin DSL)</summary>

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.minecraft-library:nbt-factory:master-SNAPSHOT")
}
```

</details>

<details>
<summary>Gradle (Groovy DSL)</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.minecraft-library:nbt-factory:master-SNAPSHOT'
}
```

</details>

<details>
<summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.minecraft-library</groupId>
    <artifactId>nbt-factory</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

</details>

To build from source:

```bash
git clone https://github.com/minecraft-library/nbt-factory.git
cd nbt-factory
./gradlew build
```

### Usage

Every format goes through `NbtFactory`. Construct one instance and reuse it.

```java
NbtFactory nbt = new NbtFactory();

// Decode a base64-encoded gzipped NBT payload (e.g. a Hypixel item)
CompoundTag root = nbt.fromBase64(base64String);

// Read typed values
String name = root.getString("id");
int count = root.getInt("Count");

// Mutate
root.put("Lore", new StringTag("Enchanted"));

// Re-encode to any format
byte[] bytes = nbt.toByteArray(root);                  // gzipped
byte[] raw   = nbt.toByteArray(root, Compression.NONE);
String snbt  = nbt.toSnbt(root);
String json  = nbt.toJson(root);
String b64   = nbt.toBase64(root);
```

Stream-based I/O:

```java
try (var in = new FileInputStream("level.dat")) {
    CompoundTag level = nbt.fromStream(in, Compression.GZIP);
}
```

### IntelliJ IDEA

Open the project root and let Gradle import. The `jmh` source set is registered as test sources via the `idea` block in `build.gradle.kts`, so `NbtBenchmarks` appears alongside the unit tests. Run configurations for JUnit tests are auto-generated.

To run a single test: right-click `NbtRoundTripTest` > **Run**.

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│  NbtFactory  (public entry point)                           │
└───────┬─────────────────────────────────────────────────────┘
        │
        ├── fromBase64 / fromByteArray / fromFile / fromStream / fromUrl
        ├── fromSnbt / fromJson
        │
        ▼
┌────────────────────────┐      ┌─────────────────────────────┐
│  io/buffer/            │      │  io/stream/                 │
│  NbtInputBuffer        │      │  NbtInputStream             │
│  NbtOutputBuffer       │      │  NbtOutputStream            │
│  (heap-backed codec)   │      │  (DataInputStream wrapper)  │
└──────────┬─────────────┘      └────────────┬────────────────┘
           │                                 │
           ▼                                 ▼
    ┌──────────────────────────────────────────────┐
    │  TagType (dispatch)                          │
    │  each variant knows its NbtByteCodec         │
    └──────────┬───────────────────────────────────┘
               │
               ▼
    ┌──────────────────────────────────────────────┐
    │  tags/                                       │
    │  ├─ primitive/  (Byte, Short, Int, …)        │
    │  ├─ array/      (ByteArray, IntArray, Long…) │
    │  └─ collection/ (Compound, List)             │
    └──────────────────────────────────────────────┘

Format wrappers:
  io/snbt/   SnbtSerializer / SnbtDeserializer   - Mojang's text format
  io/json/   NbtJsonSerializer / Deserializer    - JSON mirror of NBT
```

Compression is provided by `dev.simplified.stream.Compression` (gzip by default, passthrough available). Modified UTF-8 is handled in `NbtModifiedUtf8`.

## Benchmarks

Benchmarks live in `src/jmh/java/lib/minecraft/nbt/benchmark/NbtBenchmarks.java`. They run against two corpora:

1. **Synthetic** - a hand-built compound with one of every tag type at realistic sizes. Always available.
2. **Auction** - ~100k real SkyBlock auction-house item NBTs fetched from the public Hypixel API. Auction benchmarks are no-ops if the fixture is missing.

Generate the fixture (idempotent, no API key required):

```bash
./gradlew generateAuctionFixture
```

This writes `src/test/resources/nbt-bench-fixture/auctions.bin` (roughly 40 MB). The file is gitignored so it never bloats the repository.

Run the benchmarks:

```bash
./gradlew jmh
```

Filter to specific benchmarks:

```bash
./gradlew jmh -Pjmh.includes=".*readAuctionGzipped.*"
```

## Project Structure

```
nbt-factory/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── LICENSE.md
├── COPYRIGHT.md
├── CONTRIBUTING.md
├── CLAUDE.md
├── README.md
└── src/
    ├── main/java/lib/minecraft/nbt/
    │   ├── NbtFactory.java              # public entry point
    │   ├── exception/                   # NbtException, NbtMaxDepthException
    │   ├── io/
    │   │   ├── NbtByteCodec.java        # tag-type codec contract
    │   │   ├── NbtInput.java            # read dispatch
    │   │   ├── NbtOutput.java           # write dispatch
    │   │   ├── NbtKnownKeys.java
    │   │   ├── NbtModifiedUtf8.java     # Mojang's modified UTF-8
    │   │   ├── buffer/                  # heap-backed codec
    │   │   ├── stream/                  # DataInputStream/DataOutputStream wrappers
    │   │   ├── snbt/                    # SnbtSerializer / SnbtDeserializer
    │   │   └── json/                    # NbtJsonSerializer / NbtJsonDeserializer
    │   └── tags/
    │       ├── Tag.java                 # base contract
    │       ├── TagType.java             # enum dispatch
    │       ├── primitive/               # ByteTag, ShortTag, IntTag, ...
    │       ├── array/                   # ByteArrayTag, IntArrayTag, LongArrayTag
    │       └── collection/              # CompoundTag, ListTag
    ├── test/java/lib/minecraft/nbt/
    │   ├── NbtRoundTripTest.java
    │   └── AuctionFixtureGenerator.java
    └── jmh/java/lib/minecraft/nbt/benchmark/
        └── NbtBenchmarks.java
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and the pull-request process.

## License

This project is licensed under the **Apache License 2.0** - see [LICENSE.md](LICENSE.md) for the full text.

See [COPYRIGHT.md](COPYRIGHT.md) for third-party attribution notices, including information about the NBT specification's origin.
