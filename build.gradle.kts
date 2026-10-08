plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "me.dfrsn"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Set -PphpstormPath=/path/to/PhpStorm.app to build against a local install instead of downloading.
        val localPath = providers.gradleProperty("phpstormPath")
        if (localPath.isPresent) {
            local(localPath)
        } else {
            phpstorm("2026.2")
        }
        bundledPlugin("com.jetbrains.php")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
}
