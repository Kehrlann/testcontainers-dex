import org.jreleaser.model.Active
import org.jreleaser.model.Http

plugins {
	id("org.jreleaser") version "1.22.0"
}

group = "wf.garnier"
version = "4.0.1"

// Required for jreleaser, see:
// https://github.com/jreleaser/jreleaser/issues/1492
tasks {
	register("clean") {}
}

// ./gradlew clean
// ./gradlew publish
// ./gradlew -PcentralUsername=... -PcentralPassword=... :jreleaserPublish
val centralUsername = project.findProperty("centralUsername") as String?
val centralPassword = project.findProperty("centralPassword") as String?

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
					authorization.set(Http.Authorization.BASIC)
					username = centralUsername
					password = centralPassword
				}
				create("spring-boot-testcontainers-dex") {
					active.set(Active.ALWAYS)
					url = "https://central.sonatype.com/api/v1/publisher"
					stagingRepository("spring-boot-testcontainers-dex/build/staging-deploy")
					authorization.set(Http.Authorization.BASIC)
					username = centralUsername
					password = centralPassword
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