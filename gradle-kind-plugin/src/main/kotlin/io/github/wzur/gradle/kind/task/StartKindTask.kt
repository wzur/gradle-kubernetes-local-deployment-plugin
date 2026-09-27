package io.github.wzur.gradle.kind.task

import io.github.wzur.gradle.kind.extension.MirrorDockerRegistry
import io.github.wzur.gradle.kind.util.CommandRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.work.DisableCachingByDefault

/** Task to start a local Kubernetes cluster using Kind. */
@DisableCachingByDefault(because = "Interacts with external Kind and Docker services.")
abstract class StartKindTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    // the kind binary path
    @get:Input abstract val kindBinary: Property<String>

    @get:Input abstract val clusterName: Property<String>

    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val kindConfigFile: RegularFileProperty

    @get:OutputFile abstract val kubeConfigFile: RegularFileProperty

    @get:Optional @get:Input abstract val kubernetesVersion: Property<String>

    @get:Internal abstract val dockerRegistries: ListProperty<MirrorDockerRegistry>

    @get:Input abstract val dockerRegistriesInput: ListProperty<Map<String, Any>>

    @get:Input abstract val nodePorts: ListProperty<Int>

    @get:Optional @get:Input abstract val cloudProviderKindBinary: Property<String>

    @get:Optional @get:OutputFile abstract val cloudProviderKindPidFile: RegularFileProperty

    @TaskAction
    fun startCluster() {
        val listOfClusters =
          CommandRunner(
              listOf(
                kindBinary.get(),
                "get",
                "clusters",
              )
            )
            .run(true)
        logger.lifecycle("Creating Kind cluster with name: ${clusterName.get()}")
        val existingClusters = listOfClusters.output.lines()
        val clusterExists = existingClusters.any {
            it.trim() == clusterName.get()
        }
        val clusterHasExpectedConfiguration = hasExpectedConfiguration()

        if (clusterExists && !clusterHasExpectedConfiguration) {
            logger.lifecycle("Kind cluster configuration changed; recreating '${clusterName.get()}'.")
            val deleteCommand = listOf(kindBinary.get(), "delete", "cluster", "--name", clusterName.get())
            runCommand(deleteCommand, "kind delete cluster")
        }

        if (!clusterExists || !clusterHasExpectedConfiguration) {
            val createCommand =
              mutableListOf<String>(
                kindBinary.get(),
                "create",
                "cluster",
                "--name",
                clusterName.get(),
                "--wait",
                "60s",
              )

            if (kubernetesVersion.get().isNotEmpty()) {
                createCommand.addAll(
                  listOf(
                    "--image",
                    "kindest/node:v${kubernetesVersion.get()}",
                  )
                )
            }
            if (kindConfigFile.isPresent) {
                createCommand.addAll(
                  listOf(
                    "--config",
                    kindConfigFile.get().asFile.absolutePath,
                  )
                )
            }
            kubeConfigFile.get().asFile.parentFile.mkdirs()
            createCommand.addAll(
              listOf(
                "--kubeconfig",
                kubeConfigFile.get().asFile.absolutePath,
              )
            )
            runCommand(createCommand, "kind create cluster")
        } else {
            logger.lifecycle("Kind cluster '${clusterName.get()}' already exists.")
        }
        // now start caching registry
        dockerRegistries.get().forEach { registry ->
            // start caching registry if not started yet
            val containerName = registry.containerName.get()
            val inspectCommand =
              listOf<String>(
                "docker",
                "container",
                "inspect",
                containerName,
              )
            val inspectResult = CommandRunner(inspectCommand).run()
            if (inspectResult.exitCode != 0) {
                runCommand(
                  listOf(
                    "docker",
                    "run",
                    "--name",
                    containerName,
                    "--restart=always",
                    "--network=kind",
                    "--env",
                    "REGISTRY_PROXY_REMOTEURL=${registry.registryUrl.get()}",
                    "--detach",
                    "registry:3",
                  ),
                  "Start caching registry for ${registry.name}",
                )
            } else {
                runCommand(listOf("docker", "start", containerName), "docker container start $containerName")
            }
            // add caching registry to kind network if not already connected
            val networkInspectResult =
              CommandRunner(
                  listOf(
                    "docker",
                    "inspect",
                    "--format",
                    "{{index (index .NetworkSettings.Networks.kind \"DNSNames\") 0}}",
                    containerName,
                  )
                )
                .run()
            if (networkInspectResult.exitCode != 0) {
                runCommand(
                  listOf("docker", "network", "connect", "kind", containerName),
                  "docker network connect kind $containerName",
                )
            }
        }
        configureRegistryMirrors()
        startCloudProviderKind()
    }

    private fun startCloudProviderKind() {
        if (!cloudProviderKindBinary.isPresent) {
            return
        }

        val binary = cloudProviderKindBinary.get()
        val lockFile = Paths.get(System.getProperty("java.io.tmpdir"), "gradle-kind-cloud-provider-kind.lock")
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock =
              try {
                  channel.tryLock()
              } catch (_: OverlappingFileLockException) {
                  null
              }
            if (lock == null) {
                logger.lifecycle("cloud-provider-kind startup is already in progress")
                return
            }

            lock.use {
                if (isCloudProviderKindRunning(binary)) {
                    logger.lifecycle("cloud-provider-kind is already running")
                    return
                }

                logger.lifecycle("Starting cloud-provider-kind in the background")
                ProcessBuilder(binary)
                  .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                  .redirectError(ProcessBuilder.Redirect.DISCARD)
                  .apply { environment()["KUBECONFIG"] = kubeConfigFile.get().asFile.absolutePath }
                  .start()
                  .also { process ->
                      cloudProviderKindPidFile.get().asFile.apply {
                          parentFile.mkdirs()
                          writeText(process.pid().toString())
                      }
                  }
            }
        }
    }

    private fun isCloudProviderKindRunning(binary: String): Boolean {
        val binaryName = File(binary).name
        return ProcessHandle.allProcesses().anyMatch { process ->
            process.info().command().map { command -> File(command).name == binaryName }.orElse(false)
        }
    }

    private fun configureRegistryMirrors() {
        if (dockerRegistries.get().isEmpty()) {
            return
        }

        val nodesResult =
          CommandRunner(
              listOf(
                kindBinary.get(),
                "get",
                "nodes",
                "--name",
                clusterName.get(),
              )
            )
            .run(true)
        check(nodesResult.exitCode == 0) { "Could not list nodes for Kind cluster '${clusterName.get()}'" }

        nodesResult.output
          .lines()
          .filter { it.isNotBlank() }
          .forEach { node ->
              dockerRegistries.get().forEach { registry ->
                  val registryNames = listOf(registry.remoteRegistry.get()) + registry.aliases.get()
                  registryNames.forEach { registryName ->
                      val registryDirectory = "/etc/containerd/certs.d/$registryName"
                      runCommand(
                        listOf("docker", "exec", node, "mkdir", "-p", registryDirectory),
                        "Create registry configuration directory for $registryName in $node",
                      )
                      runCommand(
                        listOf("docker", "exec", "-i", node, "cp", "/dev/stdin", "$registryDirectory/hosts.toml"),
                        "Configure registry mirror for $registryName in $node",
                        hostsToml(registry),
                      )
                  }
              }
          }
    }

    private fun hostsToml(registry: MirrorDockerRegistry): String =
      """
      server = "${registry.registryUrl.get()}"

      [host."http://${registry.containerName.get()}:5000"]
      capabilities = ["pull", "resolve"]
      """
        .trimIndent() + "\n"

    private fun hasExpectedConfiguration(): Boolean {
        val extractions = mutableListOf<String>()
        val expectedValues = mutableListOf<String>()

        if (kubernetesVersion.get().isNotEmpty()) {
            extractions.add("{{.Config.Image}}")
            expectedValues.add("kindest/node:v${kubernetesVersion.get()}")
        }

        nodePorts.get().forEach { nodePort ->
            extractions.add("{{(index (index .NetworkSettings.Ports \"$nodePort/tcp\") 0).HostPort}}")
            expectedValues.add("$nodePort")
        }

        if (extractions.isEmpty()) {
            return true
        }

        val inspectCommand =
          mutableListOf<String>(
            "docker",
            "inspect",
            "--format",
            extractions.joinToString("|"),
            "${clusterName.get()}-control-plane",
          )
        val expectedConfigurationOutput = CommandRunner(inspectCommand).run(true)
        logger.debug(
          "> Check command returned: ${expectedConfigurationOutput.output} (${expectedConfigurationOutput.exitCode})"
        )
        logger.debug("> Expected values: ${expectedValues.joinToString("|")}")
        val expectedNodeConfiguration =
          expectedConfigurationOutput.exitCode == 0 &&
            expectedConfigurationOutput.output == expectedValues.joinToString("|")

        if (!expectedNodeConfiguration) {
            return false
        }

        dockerRegistries.get().forEach { registry ->
            val registryCheckOutput =
              CommandRunner(
                  listOf(
                    "docker",
                    "exec",
                    "${clusterName.get()}-control-plane",
                    "grep",
                    "-F",
                    "http://${registry.containerName.get()}:5000",
                    "/etc/containerd/certs.d/${registry.remoteRegistry.get()}/hosts.toml",
                  )
                )
                .run()
            if (registryCheckOutput.exitCode != 0) {
                logger.lifecycle("Kind registry configuration for ${registry.name} not found")
                return false
            }
        }

        // if we get here, all registries are configured
        return true
    }

    private fun runCommand(command: List<String>, description: String, inputText: String? = null) {
        logger.lifecycle("> ${command.joinToString(" ")}")
        val exitCode =
          execOperations
            .exec(
              Action<ExecSpec> {
                  commandLine(command)
                  inputText?.let { input ->
                      standardInput = ByteArrayInputStream(input.toByteArray())
                  }
                  standardOutput = System.out
                  errorOutput = System.err
              }
            )
            .exitValue
        check(exitCode == 0) { "$description failed with exit code $exitCode" }
    }
}
