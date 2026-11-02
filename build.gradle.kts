buildscript {
    dependencies {
        constraints {
            add("classpath", libs.build.commons.lang) {
                because("GHSA-j288-q9x7-2f5v in AGP's commons-compress path; see docs/dependency-health.md")
            }
            add("classpath", libs.build.guava) {
                because("GHSA-xxph-c9ww-hj94 in AGP tooling; preserve the JRE variant")
            }
            listOf(libs.build.bcprov, libs.build.bcpkix, libs.build.bcutil).forEach {
                add("classpath", it) {
                    because("Align BC for GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26; see docs/dependency-health.md")
                }
            }
            add("classpath", libs.build.jose4j) {
                because("GHSA-3677-xxcr-wjqv; qualified against bundletool's transparency operations")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
