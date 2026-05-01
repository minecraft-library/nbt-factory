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
// G1 is the only profile. An EpsilonGC profile was tried as an "as if mimalloc" axis
// against simdnbt but cannot run a long-enough cycle for C2 to stabilise without OOM
// at any feasible heap size (decode allocates ~6 GB/s on bigtest; the default 34s
// cycle would need ~200 GB heap). G1 already keeps GC overhead under 3% at this
// allocation rate, so it is the only honest measurement. See
// `C:\Users\BrianGraham\.claude\plans\epsilon-gc-investigation.md` for the full
// rationale.
//
// C2 inlining tweaks are applied per the simdnbt parity ResearchPack section 5.5.
// Result format is forced to JSON so tools/jmh-report.py can consume it.
//
// -PjmhInclude=<regex>     filters which benchmarks run (matched against the FQN). Without
//                            this property the full suite runs.
jmh {
    jvmArgsAppend.set(listOf(
        "-XX:MaxInlineLevel=20",
        "-XX:FreqInlineSize=500",
        "-XX:+UseG1GC"
    ))
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
