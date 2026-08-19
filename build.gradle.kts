import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

val pluginVersion = providers.gradleProperty("pluginVersion").orElse("1.0-SNAPSHOT")
val platformVersion = providers.gradleProperty("platformVersion").orElse("2026.1.4")
val localPlatformPath = providers.gradleProperty("localPlatformPath").orElse("/Applications/CLion.app")
val useLocalPlatform = providers.gradleProperty("useLocalPlatform").map(String::toBoolean).orElse(true)

group = "com.github.sammyvimes"
version = pluginVersion.get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        if (useLocalPlatform.get()) {
            local(localPlatformPath.get())
        } else {
            create("CL", platformVersion)
        }
        bundledPlugins(
            "com.intellij.platform.images",
            "com.intellij.clion",
            "com.intellij.nativeDebug",
            "com.intellij.clion-compdb",
            "org.jetbrains.plugins.clion.radler",
        )
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    splitMode = true
    pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BACKEND

    pluginConfiguration {
        changeNotes = """
      Initial version
    """.trimIndent()
    }

    pluginVerification {
        ides {
            current()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    patchPluginXml {
        sinceBuild.set("261")
        untilBuild.set("261.*")
    }
}

// Uploads the freshly built plugin jar to the remote-dev host, keeping the
// previous jar as a rollback copy. The remote backend picks the new jar up on
// its next start (reconnect from Gateway / JetBrains Client).
// Overrides: -PremoteHost=... -PremotePluginsDir=... -PremotePluginJarName=...
abstract class DeployPluginToRemoteTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFile
    abstract val pluginJar: RegularFileProperty

    @get:Input
    abstract val host: Property<String>

    // Relative to the remote home directory (avoids ~ quoting pitfalls).
    @get:Input
    abstract val pluginsDir: Property<String>

    @get:Input
    abstract val jarName: Property<String>

    @TaskAction
    fun deploy() {
        val jar = pluginJar.get().asFile
        val dir = pluginsDir.get().trimEnd('/')
        val name = jarName.get()
        execOperations.exec {
            commandLine("scp", "-o", "BatchMode=yes", jar.absolutePath, "${host.get()}:$dir/$name.uploading")
        }
        execOperations.exec {
            commandLine(
                "ssh", "-o", "BatchMode=yes", host.get(),
                "cd '$dir' && if [ -f '$name' ]; then mv -f '$name' '$name.previous'; fi && mv '$name.uploading' '$name'",
            )
        }
        logger.lifecycle(
            "Deployed ${jar.name} to ${host.get()}:$dir/$name " +
                "(previous kept as $name.previous). Restart the remote backend to pick it up.",
        )
    }
}

tasks.register<DeployPluginToRemoteTask>("deployPluginToRemote") {
    group = "intellij platform"
    description = "Update plugin.jar on the remote dev host (rotates the old jar to plugin.jar.previous)"
    pluginJar.set(tasks.named<org.gradle.jvm.tasks.Jar>("composedJar").flatMap { it.archiveFile })
    host.set(providers.gradleProperty("remoteHost").orElse("senya-ivm"))
    pluginsDir.set(providers.gradleProperty("remotePluginsDir").orElse(".local/share/JetBrains/CLion2026.1"))
    jarName.set(providers.gradleProperty("remotePluginJarName").orElse("plugin.jar"))
}
