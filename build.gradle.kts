plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.corvidlabs"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("AlgoChat")
                description.set("Kotlin implementation of the AlgoChat protocol for encrypted messaging on Algorand")
                url.set("https://github.com/CorvidLabs/kt-algochat")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("corvidlabs")
                        name.set("CorvidLabs")
                        email.set("hello@corvidlabs.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/CorvidLabs/kt-algochat.git")
                    developerConnection.set("scm:git:ssh://github.com/CorvidLabs/kt-algochat.git")
                    url.set("https://github.com/CorvidLabs/kt-algochat")
                }
            }
        }
    }
}
