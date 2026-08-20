rootProject.name = "pex"

// Top-level flat modules
include("pex-base")
include("pex-arithmetics")
include("pex-converter")
include("pex-tools")
include("pex-all")

// Parent aggregator projects (Maven pom-style)
include(":pex-sql")
include(":pex-nosql")

// Nested sub-modules (children of parent aggregators)
include("pex-sql:pex-sql-core")
include("pex-sql:pex-sql-olap")
include("pex-sql:pex-sql-streaming")
include("pex-sql:pex-sql-dialects")

include("pex-nosql:pex-nosql-core")
include("pex-nosql:pex-nosql-dialects")

// Demo module
include("pex-demos")
