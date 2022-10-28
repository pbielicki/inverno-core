plugins {
    id("java-library-conventions")
}

dependencies {
    api(project(":inverno-core-annotation"))
    implementation(libs.log4j.api)
}
