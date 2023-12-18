plugins {
    id("java-library")
    id("maven-publish")
    id("org.jreleaser") version "1.9.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "wf.garnier"
version = "3.0.2-SNAPSHOT"
val testcontainersDexVersion = "3.0.1"


repositories {
    mavenCentral()
}

dependencies {
    api("wf.garnier:testcontainers-dex:${testcontainersDexVersion}")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.2.0")
    implementation("org.springframework.boot:spring-boot-testcontainers:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client:3.2.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "Testcontainers - Dex - Spring Boot"
                description = "A Spring Boot auto-configuration for the Testcontainers Dex module"
                url = "https://github.com/Kehrlann/testcontainers-dex"
                artifactId = "spring-boot-testcontainers-dex"
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
