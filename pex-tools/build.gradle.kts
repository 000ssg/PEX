import java.awt.GraphicsEnvironment

description = "PEX Tools — Visualizer, Converter, Documentation Generator"

dependencies {
    implementation(project(":pex-base"))
}

tasks.withType<Test> {
    // GUI visualizer tests require a display — skip on headless CI (Linux).
    // macOS CI has a virtual display (Quartz), but Ubuntu does not.
    if (System.getenv("CI") == "true" && GraphicsEnvironment.isHeadless()) {
        exclude("**/visualizer/**")
    }
}
