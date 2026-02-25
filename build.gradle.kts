import org.jreleaser.model.Active

plugins {
    id("org.jreleaser") version "1.20.0"
}

group = "wf.garnier"
version = "4.0.0"

// Required for jreleaser, see:
// https://github.com/jreleaser/jreleaser/issues/1492
tasks {
    create("clean") {}
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
                create("testcontainers-dex") {
                    active.set(Active.ALWAYS)
                    url = "https://s01.oss.sonatype.org/service/local"
                    snapshotUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("testcontainers-dex/build/staging-deploy")
                    transitionMaxRetries = 100
                }
                create("spring-boot-testcontainers-dex") {
                    active.set(Active.ALWAYS)
                    url = "https://s01.oss.sonatype.org/service/local"
                    snapshotUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("spring-boot-testcontainers-dex/build/staging-deploy")
                    transitionMaxRetries = 100
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