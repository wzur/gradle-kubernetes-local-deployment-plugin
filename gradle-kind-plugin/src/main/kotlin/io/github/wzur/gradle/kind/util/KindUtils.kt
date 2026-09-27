package io.github.wzur.gradle.kind.util

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/** Constants used by the Kind plugin. */

/** The Gradle group name for tasks created by the Kind plugin. */
internal const val KIND_GRADLE_TASK_GROUP = "kind"

/** The default binary name for the Kind plugin. */
internal const val KIND_DEFAULT_BINARY = "kind"

/** The name of the folder where Kind build artifacts are stored. */
internal const val KIND_BUILD_FOLDER = "kind"

/** The default name of the Kind config template. */
internal const val KIND_DEFAULT_CLUSTER_CONFIG_TEMPLATE = "/kind/cluster.yaml"

/** The name of the Kind config file created by the plugin. */
internal const val KIND_CLUSTER_CONFIG_FILE = "cluster.yaml"

/** The default kubeconfig file created by Kind for kubectl. */
internal const val KIND_KUBECONFIG_FILE = "kubeconfig"

class CommandRunner(
  val commandLine: List<String>,
  private val logger: Logger = Logging.getLogger(CommandRunner::class.java),
) {

    fun run(captureOutput: Boolean = false): CommandOutput {
        val logMessage = commandLine.joinToString(" ")
        logger.info("> $logMessage")
        val processBuilder = ProcessBuilder(commandLine).redirectError(ProcessBuilder.Redirect.PIPE)

        if (!captureOutput) {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }
        val process = processBuilder.start()

        if (captureOutput) {
            return CommandOutput(process.inputStream.bufferedReader().use { it.readText().trim() }, process.waitFor())
        }
        return CommandOutput("", process.waitFor())
    }

    data class CommandOutput(
      val output: String,
      val exitCode: Int,
    )
}
