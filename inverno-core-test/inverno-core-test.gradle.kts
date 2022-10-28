plugins {
    id("java-common-conventions")
}

dependencies {
    testImplementation(project(":inverno-core"))
    testImplementation(project(":inverno-core-annotation"))
    testImplementation(project(":inverno-core-compiler"))
    testImplementation(project(":inverno-test"))
    testImplementation(libs.mockito.core)
    testImplementation(libs.bundles.junit)
}
