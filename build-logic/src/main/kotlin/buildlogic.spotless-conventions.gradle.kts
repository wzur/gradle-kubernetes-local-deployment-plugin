import com.diffplug.gradle.spotless.BaseKotlinExtension
import com.diffplug.spotless.kotlin.KtfmtStep

/*
 * Configure Spotless plugin
 */
plugins {
    id("com.diffplug.spotless")
}

/*
 * Configure spotless plugin
 */

val configureKotlinCommon: BaseKotlinExtension.() -> Unit = {
    ktfmt().googleStyle().configure { style ->
        style.setBlockIndent(4)
        style.setContinuationIndent(2)
        style.setMaxWidth(120)
        style.setRemoveUnusedImports(true)
        style.setTrailingCommaManagementStrategy(KtfmtStep.TrailingCommaManagementStrategy.COMPLETE)
    }
    endWithNewline()
    trimTrailingWhitespace()
}

spotless {
    kotlin {
        configureKotlinCommon()
    }
    kotlinGradle {
        configureKotlinCommon()
    }
}
