plugins {
    java
}

group = "ssg"
version = "0.1.0-SNAPSHOT"

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
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "implementation"("org.slf4j:slf4j-api:2.0.16")
        "testImplementation"("org.slf4j:slf4j-simple:2.0.16")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testImplementation"("org.mockito:mockito-core:5.14.2")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.14.2")
        "testImplementation"("org.assertj:assertj-core:3.27.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
