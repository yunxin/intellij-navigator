plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        pycharmCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("PythonCore")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "241"
            untilBuild = "251.*"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// For runIde: copy frontend plugin into sandbox and disable sandbox mode
// so the IDE loads both plugins. Build frontend first:
//   cd frontend-plugin && ./gradlew buildPlugin
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    val zipFile = file("frontend-plugin/build/distributions/intellij-navigator-frontend-1.0.0.zip")
    val sandboxPlugins = layout.buildDirectory.dir("idea-sandbox/PC-${providers.gradleProperty("platformVersion").get()}/plugins")
    doFirst {
        if (zipFile.exists()) {
            copy {
                from(zipTree(zipFile))
                into(sandboxPlugins)
            }
        }
    }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Didea.plugin.in.sandbox.mode=false")
    })
}

tasks {
    wrapper {
        gradleVersion = "8.5"
    }
}
