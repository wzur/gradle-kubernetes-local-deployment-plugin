# Gradle Kind Plugin

The Gradle Kind Plugin creates and manages a local [Kind](https://kind.sigs.k8s.io/)
Kubernetes cluster from Gradle. It is useful for local development and integration
tests that need Kubernetes.

## Requirements

- Gradle 8 or newer
- [Docker](https://docs.docker.com/get-docker/) running
- [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) installed and available as `kind`, unless `kind.version` is set
- Optional: [cloud-provider-kind](https://github.com/kubernetes-sigs/cloud-provider-kind) is used only when the `cloudProviderKind { ... }` section is present. Install it as `cloud-provider-kind`, unless `cloudProviderKind.version` is set.

## Quick start

The plugin is currently consumed from this repository. Add it as an included build
in your project's `settings.gradle.kts` (adjust the path to this checkout):

```kotlin
pluginManagement {
    includeBuild("../gradle-kubernetes-local-deployment-plugin/gradle-kind-plugin")
}
```

Apply the plugin in `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.wzur.gradle.kind")
}
```

Create the cluster:

```shell
./gradlew startKind
```

The plugin writes the kubeconfig to `build/kind/kubeconfig`. Use it with kubectl,
for example:

```shell
kubectl --kubeconfig build/kind/kubeconfig get nodes
```

Remove the cluster when finished:

```shell
./gradlew deleteKind
```

## Configuration

Configure the plugin through the top-level `kind { ... }` extension in
`build.gradle.kts` or `build.gradle`. All settings are optional. The Kotlin DSL
supports property assignment as shown below; Gradle's `.set(...)` syntax is
also available.

```kotlin
kind {
    clusterName = "demo"
    version = "0.30.0"
    kubernetesVersion = "1.36.1"
    nodePorts = listOf(30080, 30443)
    kubeConfigFile = layout.projectDirectory.file(".kind/kubeconfig")

    cloudProviderKind {
        version = "0.11.1"
    }

    mirrorDockerRegistries {
        dockerHub()
        register("gcr.io")
        register ("internal"){
            aliases = listOf("internal01")
        }
    }
}
```

The equivalent Groovy DSL is also supported:

```groovy
kind {
    clusterName = 'demo'
    nodePorts = [30080]
    mirrorDockerRegistries {
        dockerHub()
        register('gcr.io')
        register ("internal") {
            aliases = ["internal01"]
        }
    }
}
```

### Top-level properties

| Property              | Default                                                | Effect                                                                                                                                                              |
|-----------------------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kindBinary`          | `kind`, or the downloaded binary when `version` is set | Executable used for Kind commands. An explicitly set value takes precedence over the binary selected by `version`.                                                  |
| `version`             | Not set                                                | Kind release to download for the current OS and CPU architecture. The binary is cached in the Gradle user cache and reused. If unset, the plugin uses `kindBinary`. |
| `clusterName`         | Gradle project name                                    | Name used when creating, finding, or deleting the cluster.                                                                                                          |
| `kubernetesVersion`   | Kind's default for the selected Kind binary            | Kubernetes node image version. When set, the plugin requests `kindest/node:v<version>`.                                                                             |
| `nodePorts`           | Empty list                                             | TCP host ports to map to the same ports on the Kind control-plane node. These become Kind `extraPortMappings`; an empty list creates no mappings.                   |
| `kindConfigDirectory` | `build/kind`                                           | Directory for generated Kind configuration files, including `cluster.yaml`.                                                                                         |
| `kubeConfigFile`      | `build/kind/kubeconfig`                                | File where Kind writes the cluster kubeconfig. Pass it to `kubectl` with `--kubeconfig` or set `KUBECONFIG` to use it.                                              |

### `cloudProviderKind` block

The `cloudProviderKind { ... }` block is opt-in: if omitted, the plugin does
not start or manage `cloud-provider-kind`. If present, the plugin starts it in
the background after the cluster is ready and sets its `KUBECONFIG` environment
variable to the configured `kubeConfigFile`.

| Property  | Default                                                               | Effect                                                                                                                                                     |
|-----------|-----------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `binary`  | `cloud-provider-kind`, or the downloaded binary when `version` is set | Executable used to start and stop the process. An explicitly set value takes precedence over `version`.                                                    |
| `version` | Not set                                                               | Release to download for the current OS and CPU architecture. The binary is cached in the Gradle user cache and reused. If unset, the plugin uses `binary`. |

### `mirrorDockerRegistries` block

Each registry registered in this block is configured as a mirror in the Kind
cluster. The plugin starts a local Docker registry proxy container for each
entry and configures Kind's container runtime to use it. With no entries, no
proxy containers or mirror configuration are created. `dockerHub()` is a helper
for Docker Hub; use `register("host[:port]")` for another registry.

| Registry property | Default                                        | Effect                                                                                                                       |
|-------------------|------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Registry name     | The name passed to `register`                  | Registry host used as the default upstream registry.                                                                         |
| `remoteRegistry`  | Registry name                                  | Registry host that Kind's container runtime should mirror.                                                                   |
| `registryUrl`     | `https://<registry name>`                      | Upstream URL used by the local proxy container.                                                                              |
| `containerName`   | Registry name with `.` and `:` replaced by `-` | Docker container name for the local proxy. Set this if the generated name is unsuitable or conflicts with another container. |
| `aliases`         | Empty list                                     | Additional registry hostnames routed to this mirror.                                                                         |

`dockerHub()` registers `docker.io` with `https://registry-1.docker.io` as its
upstream URL and `registry-1.docker.io` as an alias. These defaults can be
overridden by configuring the registered entry.

## Tasks

| Task                           | Purpose                                                            |
|--------------------------------|--------------------------------------------------------------------|
| `startKind`                    | Creates or reuses the configured cluster and writes its kubeconfig |
| `deleteKind`                   | Deletes the Kind cluster                                           |
| `deleteCloudProviderKind`      | Stops the cloud-provider-kind process started by this project      |
| `deleteMirrorDockerRegistries` | Removes registry containers configured as mirrors                  |
| `deleteKindAll`                | Deletes the cluster and all mirror registry containers             |
| `debugKind`                    | Prints the resolved plugin configuration                           |

Run `./gradlew debugKind` when checking configuration before starting Docker or Kind.
