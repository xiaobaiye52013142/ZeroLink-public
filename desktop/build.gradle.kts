import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(project(":shared"))
                implementation(libs.jmdns)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.helium4.localscreenshare.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "ZeroLink"
            packageVersion = "1.0.0"
            description = "ZeroLink — Share screens between phone and PC"
            includeAllModules = true

            macOS {
                // Do not attempt code signing in CI — no Developer ID certificate available.
                // Users on macOS will need to right-click → Open on first launch.
                signing {
                    sign.set(false)
                }
            }
        }
    }
}
