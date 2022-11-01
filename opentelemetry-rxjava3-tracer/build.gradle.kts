plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "6.11.0"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    api("org.apache.commons:commons-math3:3.6.1")

    implementation("com.google.guava:guava:31.1-jre")
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
            //artifactId = "my-library"
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
    repositories {
        maven {
            // change URLs to point to your repos, e.g. http://my.org/repo
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = findProperty("ossrhUsername") as? String ?: "unset"
                password =  findProperty("ossrhPassword") as? String ?: "unset"
            }
        }
    }
}

signing {
    //useGpgCmd()
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
