plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "6.22.0"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    api(platform("org.apache.logging.log4j:log4j-bom:2.21.1"))
    api(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:1.28.0-alpha"))

    api("io.opentelemetry:opentelemetry-api")
    api("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-2.0")

    testImplementation("org.apache.logging.log4j:log4j-core")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    testImplementation("org.apache.logging.log4j:log4j-jcl")
    testImplementation("org.apache.logging.log4j:log4j-jpl")
    testImplementation("org.apache.logging.log4j:log4j-jul")

    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("com.google.truth.extensions:truth-java8-extension:1.1.5")
    testImplementation("com.google.truth:truth:1.1.5")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("RxJava OpenTelemetry tracing")
                description.set("Utility methods to simplify tracing RxJava")
                url.set("https://github.com/ikstewa/opentelemetry-rxjava-tracer/")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("ikstewa")
                        name.set("Ian Stewart")
                        url.set("https://github.com/ikstewa/")
                    }
                }
                scm {
                    url.set("https://github.com/ikstewa/opentelemetry-rxjava-tracer/")
                    connection.set("scm:git:git://github.com/ikstewa/opentelemetry-rxjava-tracer/")
                    developerConnection.set("scm:git:ssh://github.com/ikstewa/opentelemetry-rxjava-tracer/")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        csv.required.set(true)
    }
}

spotless {
    // generic formatting for miscellaneous files
    format("misc") {
        target("*.gradle.kts", "*.gradle", "*.md", ".gitignore")

        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }

    // chose the Google java formatter, version 1.9
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat()

        // and apply a license header
        licenseHeaderFile(rootProject.file("HEADER"))
    }
}
