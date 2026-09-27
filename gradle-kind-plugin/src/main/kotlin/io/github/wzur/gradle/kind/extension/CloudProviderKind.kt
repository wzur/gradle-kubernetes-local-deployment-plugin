package io.github.wzur.gradle.kind.extension

import io.github.wzur.gradle.kind.util.BinaryDownloader
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/** Configuration for the optional cloud-provider-kind process. */
abstract class CloudProviderKind @Inject constructor(objects: ObjectFactory) {
    /** The path to the cloud-provider-kind binary. A user-set value overrides the version-based convention. */
    abstract val binary: Property<String>

    /** The version of cloud-provider-kind to download. If not set, uses the `cloud-provider-kind` command. */
    abstract val version: Property<String>

    private var requested = false

    init {
        version.convention("")
        binary.convention(
          version.map { version -> BinaryDownloader().resolveCloudProviderKindBinary(version, "cloud-provider-kind") }
        )
    }

    internal fun request() {
        requested = true
    }

    internal fun isRequested(): Boolean = requested
}
