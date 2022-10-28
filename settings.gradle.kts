pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

include("inverno-core")
include("inverno-core-annotation")
include("inverno-core-compiler")
include("inverno-core-test")
include("inverno-test")

rootProject.name = "inverno"

renameBuildFiles(rootProject)

fun renameBuildFiles(project: ProjectDescriptor) {
    project.buildFileName = if (File(
                    project.projectDir,
                    "${project.name}.gradle.kts"
            ).isFile
    ) "${project.name}.gradle.kts" else "${project.name}.gradle"
    project.children.forEach {
        renameBuildFiles(it)
    }
}