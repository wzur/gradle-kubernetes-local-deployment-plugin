package io.github.wzur.gradle.kind.task

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Task to stop the cloud-provider-kind process started by this project. */
@DisableCachingByDefault(because = "Interacts with external cloud-provider-kind service.")
abstract class DeleteCloudProviderKindTask @Inject constructor() : DefaultTask() {
    @get:Optional @get:Input abstract val cloudProviderKindBinary: Property<String>

    @get:Internal abstract val cloudProviderKindPidFile: RegularFileProperty

    @TaskAction
    fun deleteCloudProviderKind() {
        if (!cloudProviderKindPidFile.isPresent) {
            return
        }

        val pidFile = cloudProviderKindPidFile.get().asFile
        if (!pidFile.isFile) {
            return
        }

        val pid = pidFile.readText().trim().toLongOrNull()
        val process = pid?.let { ProcessHandle.of(it).orElse(null) }
        if (
          process != null &&
            cloudProviderKindBinary.isPresent &&
            isMatchingProcess(process, cloudProviderKindBinary.get())
        ) {
            logger.lifecycle("Stopping cloud-provider-kind started by this project")
            process.destroyForcibly()
        }
        pidFile.delete()
    }

    private fun isMatchingProcess(process: ProcessHandle, binary: String): Boolean =
      process.info().command().map { command -> File(command).name == File(binary).name }.orElse(false)
}
