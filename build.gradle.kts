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
