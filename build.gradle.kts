import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
}

group = "ssg"
version = "0.2.0-SNAPSHOT"

// Centralize version constants from gradle.properties (using non-deprecated API for Gradle 9.x)
val junitVersion = property("junitVersion") as String
val junitPlatformVersion = property("junitPlatformVersion") as String
val mockitoVersion = property("mockitoVersion") as String
val assertjVersion = property("assertjVersion") as String
val slf4jVersion = property("slf4jVersion") as String

subprojects {
    apply(plugin = "java-library")

    group = "ssg"
    version = rootProject.version

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("--enable-preview")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
        // Disable parallel test execution
        maxParallelForks = 1
        dependsOn(tasks.named("jar"))
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        "implementation"("org.slf4j:slf4j-api:$slf4jVersion")
        "testImplementation"("org.slf4j:slf4j-simple:$slf4jVersion")
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.mockito:mockito-core:$mockitoVersion")
        "testImplementation"("org.mockito:mockito-junit-jupiter:$mockitoVersion")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
    }
}


// ── Maven Publish — for publishing to local repo or GitHub Packages ──
val parentAggregatorProjects = setOf("pex-sql", "pex-nosql")

subprojects.forEach { subproject ->
    subproject.plugins.apply("maven-publish")
    
    subproject.configure<PublishingExtension> {
        publications.create("maven", MavenPublication::class.java) {
            // For parent aggregator projects, publish as POM
            if (subproject.name in parentAggregatorProjects) {
                pom {
                    packaging = "pom"
                }
            } else {
                from(subproject.components.getByName("java"))
            }
            
            groupId = subproject.group.toString()
            artifactId = subproject.name
            version = subproject.version.toString()
            
            pom {
                name.set(subproject.name)
                description.set(subproject.description ?: "PEX ${subproject.name} module")
                url.set("https://github.com/000ssg/PEX")
                
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("000ssg")
                        name.set("Sergey Sidorov")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:000ssg/PEX.git")
                    developerConnection.set("scm:git:git@github.com:000ssg/PEX.git")
                    url.set("https://github.com/000ssg/PEX")
                }
            }
        }
        
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/000ssg/PEX")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR") ?: "000ssg"
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("PACKAGE_PAT") ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
