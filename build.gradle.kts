plugins {
    id("java-library")
    `maven-publish`
    alias(libs.plugins.jmh)
    idea
}

group = "dev.sbs"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Annotations
    annotationProcessor(libs.simplified.annotations)

    // JetBrains Annotations (@NotNull / @Nullable / @PrintFormat)
    api(libs.jetbrains.annotations)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Gson - SNBT/JSON serializers + fixture generator
    api(libs.gson)

    // Simplified Libraries - Compression, StringUtil
    api("com.github.simplified-dev:utils:master-SNAPSHOT")

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
}

tasks {
    test {
        useJUnitPlatform()
    }
    register<JavaExec>("generateAuctionFixture") {
        description = "Fetches the full SkyBlock auction house and writes the JMH benchmark fixture (idempotent, no API key required)."
        group = "jmh"
        mainClass.set("lib.minecraft.nbt.AuctionFixtureGenerator")
        classpath = sourceSets["test"].runtimeClasspath
    }
    withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }
}

// JMH wiring.
//
// -PjmhJvmProfile=g1       (default) - G1 collector, the realistic baseline.
// -PjmhJvmProfile=epsilon  - Epsilon (no-op) GC for an "as if mimalloc" comparison
//                            against simdnbt. Requires that the bench heap fits in -Xmx.
//
// Both profiles append C2 inlining tweaks per the simdnbt parity ResearchPack section 5.5.
// Result format is forced to JSON so tools/jmh-report.py can consume it.
//
// -PjmhInclude=<regex>     filters which benchmarks run (matched against the FQN). Without
//                            this property the full suite runs.
jmh {
    val profile = (project.findProperty("jmhJvmProfile") as String?) ?: "g1"
    val args = mutableListOf("-XX:MaxInlineLevel=20", "-XX:FreqInlineSize=500")
    when (profile.lowercase()) {
        "epsilon" -> {
            // EpsilonGC never collects; the heap fills up monotonically. Decode benches
            // allocate hundreds of MB/s of tag trees, so the long-default warmup/iteration
            // counts (3x3s warmup + 5x5s measurement = 34s/param) cannot fit even in 16 GiB.
            // Pre-touch the whole heap to avoid page-fault noise in the timed region.
            args.addAll(listOf(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseEpsilonGC",
                "-XX:+AlwaysPreTouch",
                "-Xmx16g"
            ))
            // Keep the total allocated heap under -Xmx by shortening iterations. The bench
            // is still long enough for C2 to stabilise but short enough to avoid OOM on the
            // smallest fixtures (bigtest.nbt at ~1.5 KB hits >300M iterations/s, allocating
            // a tag tree on every one). Even at 16 GiB we need to keep total wall-clock per
            // @Param under ~5s. 1x1s warmup + 2x1s measurement = 3s/param worst case.
            warmupIterations.set(1)
            warmup.set("1s")
            iterations.set(2)
            timeOnIteration.set("1s")
        }
        else -> args.add("-XX:+UseG1GC")
    }
    jvmArgsAppend.set(args)
    resultFormat.set("JSON")

    (project.findProperty("jmhInclude") as String?)?.let {
        includes.set(listOf(it))
    }
}

idea {
    module {
        testSources.from(sourceSets["jmh"].java.srcDirs)
        testResources.from(sourceSets["jmh"].resources.srcDirs)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("nbt-factory")
                description.set("Minecraft NBT reader/writer for Java 21 - binary, SNBT, JSON, base64.")
                url.set("https://github.com/minecraft-library/nbt-factory")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
