plugins {
    kotlin("multiplatform") version "1.8.20-RC"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(8)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.0-Beta")
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting
    }
}
