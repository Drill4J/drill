import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("kotlinx-serialization")
    id("com.google.cloud.tools.jib") version "1.6.1"
    application
    `maven-publish`
    idea
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

setupVersion()

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kodein-framework/Kodein-DI/")
    if ("$version".endsWith("-SNAPSHOT")) {
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    }
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
}

val appMainClassName by extra("io.ktor.server.netty.EngineMain")

val appJvmArgs = listOf(
    "-server",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
    "-Djava.awt.headless=true",
    "-Xms128m",
    "-Xmx2g",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=100"
)


application {
    mainClassName = appMainClassName
    applicationDefaultJvmArgs = appJvmArgs
}

val remotePlugins: Configuration by configurations.creating {}
dependencies {
    remotePlugins("com.epam.drill:coverage-plugin:0.4.0-SNAPSHOT")
}
val integrationTestImplementation by configurations.creating {
    extendsFrom(configurations["testCompile"])
}
val integrationTestRuntime by configurations.creating {
    extendsFrom(configurations["testRuntime"])
}



dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("server-netty"))
    implementation(ktor("locations"))
    implementation(ktor("server-core"))
    implementation(ktor("websockets"))
    implementation(drill("drill-admin-part-jvm"))
    implementation(drill("common-jvm", drillCommonLibVersion))
    implementation("com.h2database:h2:1.4.197")
    implementation("com.zaxxer:HikariCP:2.7.8")
    implementation("com.hazelcast:hazelcast:3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation("org.jetbrains.exposed:exposed:0.13.7")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    implementation("io.github.microutils:kotlin-logging:1.6.24")
    implementation("org.slf4j:slf4j-simple:1.7.26")
    implementation("ch.qos.logback:logback-classic:1.0.13")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:5.5.0.201909110433-r")
    testImplementation(kotlin("test-junit"))
    integrationTestImplementation(ktor("server-test-host"))
    integrationTestImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")

}

jib {
    from {
        image = "gcr.io/distroless/java:8"
    }
    to {
        image = "drill4j/${project.name}"
        tags = setOf("${project.version}")
    }
    container {
        ports = listOf("8090", "5006")
        mainClass = appMainClassName

        jvmFlags = appJvmArgs
    }
}
val testIngerationModuleName = "test-integration"

sourceSets {
    create(testIngerationModuleName) {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src/$testIngerationModuleName/kotlin")
            resources.srcDir("src/$testIngerationModuleName/resources")
            compileClasspath += sourceSets["main"].output + integrationTestImplementation + configurations["testRuntimeClasspath"]
            runtimeClasspath += output + compileClasspath + sourceSets["test"].runtimeClasspath + integrationTestRuntime
        }
    }
}
idea {
    module {
        testSourceDirs = (sourceSets[testIngerationModuleName].withConvention(KotlinSourceSet::class) { kotlin.srcDirs})
        testResourceDirs = (sourceSets[testIngerationModuleName].resources.srcDirs)
        scopes["TEST"]?.get("plus")?.add(integrationTestImplementation)
        println( scopes["TEST"])
    }
}

task<Test>("integrationTest") {
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets[testIngerationModuleName].output.classesDirs
    classpath = sourceSets[testIngerationModuleName].runtimeClasspath
    mustRunAfter(tasks["test"])
}

tasks.named("check") {
    dependsOn("integrationTest")
}

tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
    }

    val downloadPlugins by registering(Copy::class) {
        from(remotePlugins.files.filter { it.extension == "zip" })
        into(rootDir.resolve("distr").resolve("adminStorage"))
    }

    named("run") {
        dependsOn(downloadPlugins)
    }
    
    named("jib") {
        dependsOn(downloadPlugins)
    }
}

publishing {
    repositories {
        maven {
            url =
                if (version.toString().endsWith("-SNAPSHOT"))
                    uri("http://oss.jfrog.org/oss-snapshot-local")
                else uri("http://oss.jfrog.org/oss-release-local")
            credentials {
                username =
                    if (project.hasProperty("bintrayUser"))
                        project.property("bintrayUser").toString()
                    else System.getenv("BINTRAY_USER")
                password =
                    if (project.hasProperty("bintrayApiKey"))
                        project.property("bintrayApiKey").toString()
                    else System.getenv("BINTRAY_API_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>("admin") {
            artifact(tasks["shadowJar"])
        }
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module${version?.let { ":$version" } ?: ""}"

@Suppress("unused")
fun DependencyHandler.drill(module: String, version: Any? = project.version): Any =
    "com.epam.drill:$module${version?.let { ":$version" } ?: ""}"

fun DependencyHandler.`integrationTestImplementation`(dependencyNotation: Any): Dependency? =
    add("integrationTestImplementation", dependencyNotation)