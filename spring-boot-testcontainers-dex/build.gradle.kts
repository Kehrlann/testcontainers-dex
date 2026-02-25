plugins {
    id("java-library")
    id("maven-publish")
}

group = "wf.garnier"
version = rootProject.version
val bootVersion = "4.0.3"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":testcontainers-dex"))
    implementation("org.springframework.boot:spring-boot-web-server:${bootVersion}")
    implementation("org.springframework.boot:spring-boot-testcontainers:${bootVersion}")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client:${bootVersion}")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
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