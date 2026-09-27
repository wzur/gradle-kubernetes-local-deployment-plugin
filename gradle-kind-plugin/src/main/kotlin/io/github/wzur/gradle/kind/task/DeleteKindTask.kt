package io.github.wzur.gradle.kind.task

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/** Task to delete a local Kind cluster. */
@DisableCachingByDefault(because = "Interacts with external Kind and Docker services.")
abstract class DeleteKindTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input abstract val kindBinary: Property<String>

    @get:Input abstract val clusterName: Property<String>

    @get:OutputFile abstract val kubeConfigFile: RegularFileProperty

    @TaskAction
    fun deleteCluster() {
        execOperations.exec {
            commandLine(
              kindBinary.get(),
              "delete",
              "cluster",
              "--name",
              clusterName.get(),
              "--kubeconfig",
              kubeConfigFile.get().asFile.absolutePath,
            )
        }
    }
}
