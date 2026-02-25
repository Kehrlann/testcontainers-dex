plugins {
    id("java-library")
    id("maven-publish")
    id("com.google.protobuf") version "0.9.6"
}

group = "wf.garnier"
version = rootProject.version

val grpcVersion = "1.79.0"
val protobufVersion = "3.25.8"
val protocVersion = protobufVersion

repositories {
    mavenCentral()
}

dependencies {
    api("org.testcontainers:testcontainers:2.0.3")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-services:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("tools.jackson.core:jackson-databind:3.1.0")
    testImplementation("org.apache.httpcomponents.core5:httpcore5:5.4.1")
    testImplementation("ch.qos.logback:logback-core:1.5.32")
    testImplementation("ch.qos.logback:logback-classic:1.5.32")
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
            named("java") {}
        }
        plugins {
            create("grpc") {}
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
