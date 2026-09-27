package io.github.wzur.gradle.kind.extension

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * The KindExtension class defines a custom extension for the plugin. This allows users to configure the plugin in their
 * build script via a DSL block, e.g.:
 *
 * kind { clusterName = "..." }
 *
 * The `abstract val` declarations are Gradle managed properties: Gradle generates the implementation at runtime through
 * class decoration and creates the `Property<T>` instances automatically. There is no need to declare a constructor or
 * to inject an `ObjectFactory` manually.
 */
abstract class KindExtension @Inject constructor(objects: ObjectFactory) {
    /** The path to the Kind binary to use. A user-set value overrides the version-based convention. */
    abstract val kindBinary: Property<String>

    /** The version of the Kind binary to download. If not set, uses `kindBinary`. */
    abstract val version: Property<String>

    /** The name of the Kind cluster to be created. */
    abstract val clusterName: Property<String>

    /**
     * The version of Kubernetes to be used in the Kind cluster. If not set, Kind will use its own default version. That
     * version is specific for the Kind binary used.
     */
    abstract val kubernetesVersion: Property<String>

    /** The list of node ports to be used in the Kind cluster. If not set, defaults to an empty list. */
    abstract val nodePorts: ListProperty<Int>

    /** The directory where Kind configuration files are generated. If not set, defaults to build/kind. */
    abstract val kindConfigDirectory: DirectoryProperty

    /** The kubeconfig file written by Kind for kubectl. If not set, defaults to build/kind/kubeconfig. */
    abstract val kubeConfigFile: RegularFileProperty

    /** The list of Docker registries to be mirrored in the Kind cluster. If not set, defaults to an empty list. */
    val mirrorDockerRegistries: MirrorDockerRegistries = MirrorDockerRegistries(objects)

    /** Optional configuration for the cloud-provider-kind process. */
    val cloudProviderKind: CloudProviderKind = objects.newInstance(CloudProviderKind::class.java)

    /**
     * The convenience method for Gradle to run when it configures the list of Docker registries to be mirrored in the
     * Kind cluster.
     *
     * @param action The action to execute on the mirrorDockerRegistries property.
     */
    fun mirrorDockerRegistries(action: Action<in MirrorDockerRegistries>) = action.execute(mirrorDockerRegistries)

    /** Requests that cloud-provider-kind is started after the Kind cluster is ready. */
    fun cloudProviderKind(action: Action<in CloudProviderKind>) {
        cloudProviderKind.request()
        action.execute(cloudProviderKind)
    }
}
