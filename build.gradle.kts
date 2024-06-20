import net.researchgate.release.ReleaseExtension

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("net.researchgate.release") version "3.0.2"
    id("io.freefair.github.dependency-submission") version "6.6.3"
}

configure<ReleaseExtension> {
    with(git) {
        requireBranch.set("master")
    }
}

nexusPublishing {
    repositories {
        sonatype {  //only for users registered in Sonatype after 24 Feb 2021
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

            // defaults to project.properties["myNexusUsername"]
            username.set(findProperty("ossrhUsername") as? String ?: "unset")
            // defaults to project.properties["myNexusPassword"]
            password.set(findProperty("ossrhPassword") as? String ?: "unset")
        }
    }
}
