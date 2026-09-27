package io.github.wzur.gradle.kind.task

import io.github.wzur.gradle.kind.util.KIND_BUILD_FOLDER
import io.github.wzur.gradle.kind.util.KIND_CLUSTER_CONFIG_FILE
import io.github.wzur.gradle.kind.util.KIND_DEFAULT_CLUSTER_CONFIG_TEMPLATE
import java.net.URI
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile

/** Task to prepare kind config file */
@CacheableTask
abstract class PrepareKindConfig : Copy() {
    @get:Input abstract val kindConfigTemplate: Property<URI>

    @get:OutputFile abstract val kindConfigFile: RegularFileProperty

    @get:Internal abstract val kindConfigDirectory: DirectoryProperty

    init {
        kindConfigTemplate.convention(this.javaClass.getResource(KIND_DEFAULT_CLUSTER_CONFIG_TEMPLATE)?.toURI())
        kindConfigDirectory.convention(project.layout.buildDirectory.dir(KIND_BUILD_FOLDER))
        kindConfigFile.convention(kindConfigDirectory.file(KIND_CLUSTER_CONFIG_FILE))
    }
}
