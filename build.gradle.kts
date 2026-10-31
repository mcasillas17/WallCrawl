buildscript {
    dependencies {
        constraints {
            add("classpath", libs.build.commons.lang) {
                because("GHSA-j288-q9x7-2f5v in AGP's commons-compress path; see docs/dependency-health.md")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
