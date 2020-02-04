plugins {
    id("kotlin-multiplatform")
    id("kotlinx-serialization")
}
repositories {
    mavenCentral()
    jcenter()
}

kotlin {
    targets {
        mingwX64()
        linuxX64("linuxX64")
        jvm()
        macosX64("macosX64")

    }

    sourceSets {
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
            }
        }

        val commonMain by getting
        commonMain.apply {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationRuntimeVersion")
            }
        }


        val commonNativeSs by creating {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-native:$serializationRuntimeVersion")
            }
        }
        @Suppress("UNUSED_VARIABLE") val mingwX64Main by getting { dependsOn(commonNativeSs) }
        @Suppress("UNUSED_VARIABLE") val linuxX64Main by getting { dependsOn(commonNativeSs) }
        @Suppress("UNUSED_VARIABLE") val macosX64Main by getting { dependsOn(commonNativeSs) }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions>> {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
}
tasks.build {
    dependsOn("publishToMavenLocal")
}
