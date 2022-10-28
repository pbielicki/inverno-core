pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "inverno"

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