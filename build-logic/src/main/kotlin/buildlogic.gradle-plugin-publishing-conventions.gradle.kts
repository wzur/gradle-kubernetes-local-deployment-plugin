plugins {
    `maven-publish`
    id("com.gradle.plugin-publish")
}

group = "io.github.wzur.gradle"

gradlePlugin {
    website = "https://github.com/wzur/gradle-kubernetes-local-deployment-plugin"
    vcsUrl = "https://github.com/wzur/gradle-kubernetes-local-deployment-plugin.git"
}

publishing {
    repositories {
        mavenLocal()
    }
}
