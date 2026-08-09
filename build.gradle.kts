import org.gradle.api.tasks.testing.Test

plugins {
    java
}

group = "ssg"
version = "0.1.0-SNAPSHOT"

// Centralize version constants from gradle.properties (using non-deprecated API for Gradle 9.x)
val junitVersion = property("junitVersion") as String
val junitPlatformVersion = property("junitPlatformVersion") as String
val mockitoVersion = property("mockitoVersion") as String
val assertjVersion = property("assertjVersion") as String
val slf4jVersion = property("slf4jVersion") as String

subprojects {
    apply(plugin = "java")

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
