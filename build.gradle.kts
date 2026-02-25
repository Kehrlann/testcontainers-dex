import org.jreleaser.model.Active

plugins {
	id("org.jreleaser") version "1.22.0"
}

group = "wf.garnier"
version = "4.0.0"

// Required for jreleaser, see:
// https://github.com/jreleaser/jreleaser/issues/1492
tasks {
	register("clean") {}
}


jreleaser {
	gitRootSearch.set(true)
	signing {
		pgp {
			active = Active.ALWAYS
			armored = true
		}
	}
	deploy {
		maven {
			mavenCentral {
				create("testcontainers-dex") {
					active.set(Active.ALWAYS)
					url = "https://central.sonatype.com/api/v1/publisher"
					stagingRepository("testcontainers-dex/build/staging-deploy")
				}
				create("spring-boot-testcontainers-dex") {
					active.set(Active.ALWAYS)
					url = "https://central.sonatype.com/api/v1/publisher"
					stagingRepository("spring-boot-testcontainers-dex/build/staging-deploy")
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