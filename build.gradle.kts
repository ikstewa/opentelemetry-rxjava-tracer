import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.vanniktech.maven.publish") version "0.22.0"
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.DEFAULT)
    // or when publishing to https://s01.oss.sonatype.org
    //publishToMavenCentral(SonatypeHost.S01)

    signAllPublications()
}

group = "io.github.ikstewa"
version = project.version