package io.github.wzur.gradle.kind.extension

import groovy.lang.Closure
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class MirrorDockerRegistry
@Inject
constructor(
  private val registryName: String,
  objects: ObjectFactory,
) : Named {
    override fun getName(): String = registryName

    // alternative names for the same registry
    val aliases: ListProperty<String> = objects.listProperty(String::class.java)

    fun setAliases(value: List<String>) = aliases.set(value)

    abstract val containerName: Property<String>
    abstract val registryUrl: Property<String>
    abstract val remoteRegistry: Property<String>

    init {
        aliases.convention(emptyList())
        containerName.convention(name.replace(".", "-").replace(":", "-"))
        registryUrl.convention("https://$name")
        remoteRegistry.convention(name)
    }

    /** a helper function to convert the registry to a map, useful for passing to the kind config template */
    fun toMap(): Map<String, Any> {
        return mapOf(
          "name" to name,
          "containerName" to containerName.get(),
          "remoteRegistry" to remoteRegistry.get(),
          "registryUrl" to registryUrl.get(),
          "aliases" to aliases.get(),
        )
    }
}

/** This behaves like a NamedDomainObjectContainer<MirrorDockerRegistry>, but adds the custom `dockerHub()` helper. */
class MirrorDockerRegistries(objects: ObjectFactory) : Iterable<MirrorDockerRegistry> {

    private val container: NamedDomainObjectContainer<MirrorDockerRegistry> =
      objects.domainObjectContainer(MirrorDockerRegistry::class.java)

    fun dockerHub(): NamedDomainObjectProvider<MirrorDockerRegistry> =
      container.register(
        "docker.io",
        Action<MirrorDockerRegistry> {
            registryUrl.set("https://registry-1.docker.io")
            aliases.add("registry-1.docker.io")
        },
      )

    /*
     * This overload is used by Groovy DSL.
     */
    fun register(
      name: String,
      action: Action<in MirrorDockerRegistry>,
    ): NamedDomainObjectProvider<MirrorDockerRegistry> = container.register(name, action)

    fun register(
      name: String,
      configure: Closure<*>,
    ): NamedDomainObjectProvider<MirrorDockerRegistry> =
      container.register(name) {
          configure.delegate = this
          configure.resolveStrategy = Closure.DELEGATE_FIRST
          configure.call()
      }

    /*
     * This overload gives Kotlin DSL a receiver-style lambda.
     *
     * @JvmName avoids a JVM signature clash with the Action overload.
     */
    @JvmName("registerWithKotlinConfiguration")
    fun register(
      name: String,
      configure: MirrorDockerRegistry.() -> Unit,
    ): NamedDomainObjectProvider<MirrorDockerRegistry> = container.register(name, configure)

    /*
     * This overload gives Kotlin DSL a receiver-style lambda.
     */
    fun register(name: String): NamedDomainObjectProvider<MirrorDockerRegistry> = container.register(name)

    fun named(name: String): NamedDomainObjectProvider<MirrorDockerRegistry> = container.named(name)

    fun configureEach(action: Action<in MirrorDockerRegistry>) {
        container.configureEach(action)
    }

    override fun iterator(): Iterator<MirrorDockerRegistry> = container.iterator()

    fun isNotEmpty(): Boolean = container.isNotEmpty()
}
