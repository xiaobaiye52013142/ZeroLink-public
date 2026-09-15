plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    val jvmCommon by sourceSets.creating {
        dependsOn(sourceSets["commonMain"])
    }
    sourceSets["jvmMain"].dependsOn(jvmCommon)
    sourceSets["androidMain"].dependsOn(jvmCommon)
}

android {
    namespace = "com.helium4.localscreenshare.shared"
    compileSdk = 36
}
