import org.jreleaser.model.Active

plugins {
    id("java-library")
    id("maven-publish")
    id("org.jreleaser") version "1.9.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "wf.garnier"
version = "3.0.0-SNAPSHOT"

val grpcVersion = "1.60.0"
val protobufVersion = "3.25.0"
val protocVersion = protobufVersion

repositories {
    mavenCentral()
}

dependencies {
    api("org.testcontainers:testcontainers:1.19.3")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-services:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    testImplementation("org.apache.httpcomponents.core5:httpcore5:5.2.1")
    testImplementation("ch.qos.logback:logback-core:1.4.11")
    testImplementation("ch.qos.logback:logback-classic:1.4.11")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.javadoc {
    excludes.add("wf/garnier/testcontainers/dexidp/grpc/**")
}

tasks.test {
    useJUnitPlatform()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protocVersion}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
    }

    tasks.generateProto {
        builtins {
            named("java") {
                option("lite")
            }
        }
        plugins {
            create("grpc") {
                option("lite")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "Testcontainers - Dex"
                description = "A Testcontainers Module for the Dex OpenID Provider"
                url = "https://github.com/Kehrlann/testcontainers-dex"
                artifactId = "testcontainers-dex"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "kehrlann"
                        name = "Daniel Garnier-Moiroux"
                        email = "git@garnier.wf"
                    }
                }
                scm {
                    connection = "https://github.com/Kehrlann/testcontainers-dex.git"
                    url = "https://github.com/Kehrlann/testcontainers-dex"
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    gitRootSearch.set(true)
    signing {
        active = Active.ALWAYS
        armored = true
    }
    deploy {
        maven {
            nexus2 {
                create("maven-central") {
                    active.set(Active.ALWAYS)
                    url = "https://s01.oss.sonatype.org/service/local"
                    snapshotUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
    release {
        github {
            repoOwner = "Kehrlann"
            overwrite = true
        }
    }
}