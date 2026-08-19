// PEX Demos — usage examples and scenario demonstrations
// Excluded from Maven publish lifecycle; for reference only.

val javaRelease = "25"
val slf4jVersion = "2.0.16"

plugins { `java-library` }

group = "ssg"
version = rootProject.version

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaCompile> {
    options.release.set(javaRelease.toInt())
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

// Demos are not published to Maven
afterEvaluate {
    plugins.withId("maven-publish") {
        // Remove the maven-publish plugin to exclude demos from publishing
    }
}

dependencies {
    // PEX modules
    api(project(":pex-base"))
    api(project(":pex-arithmetics"))
    api(project(":pex-converter"))
    api(project(":pex-all"))
    api(project(":pex-sql:pex-sql-core"))
    api(project(":pex-sql:pex-sql-olap"))
    api(project(":pex-sql:pex-sql-dialects"))
    api(project(":pex-sql:pex-sql-streaming"))
    api(project(":pex-nosql:pex-nosql-core"))
    api(project(":pex-nosql:pex-nosql-dialects"))
    api(project(":pex-tools"))

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
}
