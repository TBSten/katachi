// The application of this sample, and deliberately the least interesting part of it: three
// files that exist so the roles in `:architecture-test` have something to cover. What this
// sample is about lives in `architecture-test/src/test/kotlin/com/example/processors/`.
plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.example"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Nothing. The application has no dependency of its own, and katachi is not one either:
    // the architecture definition, the processors and the test that asserts them all live in
    // `:architecture-test`, a module of their own.
}
