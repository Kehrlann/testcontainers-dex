plugins {
    id("java-library")
    id("maven-publish")
}

group = "wf.garnier"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.testcontainers:testcontainers:1.19.3")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    testImplementation("org.apache.httpcomponents.core5:httpcore5:5.2.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}
