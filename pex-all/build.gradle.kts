description = "PEX All — Integration tests"

description = "PEX All — Integration tests"

dependencies {
    implementation(project(":pex-base"))
    implementation(project(":pex-arithmetics"))
    implementation(project(":pex-converter"))
    implementation(project(":pex-sql:pex-sql-core"))
    implementation(project(":pex-sql:pex-sql-olap"))
    implementation(project(":pex-sql:pex-sql-streaming"))
    implementation(project(":pex-sql:pex-sql-dialects"))
    implementation(project(":pex-nosql:pex-nosql-core"))
    implementation(project(":pex-nosql:pex-nosql-dialects"))
}
