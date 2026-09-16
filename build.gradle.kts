import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.boehmer"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath")
        if (localIde.isPresent) {
            local(localIde)
        } else {
            webstorm(providers.gradleProperty("platformVersion"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        // DesktopLayout is internal API but the only way to read/apply a layout; usages are expected.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        )
        ides {
            val localIde = providers.gradleProperty("localIdePath")
            if (localIde.isPresent) local(localIde) else recommended()
        }
    }
}
