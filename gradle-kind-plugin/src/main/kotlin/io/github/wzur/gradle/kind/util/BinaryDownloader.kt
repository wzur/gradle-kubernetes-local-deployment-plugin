package io.github.wzur.gradle.kind.util

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.util.Locale
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

enum class BinaryOperatingSystem(val id: String) {
    LINUX("linux"),
    MACOS("darwin"),
    WINDOWS("windows");

    companion object {
        fun current(): BinaryOperatingSystem {
            val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
            return when {
                osName.startsWith("linux") -> LINUX
                osName.startsWith("mac") || osName.startsWith("darwin") -> MACOS
                osName.startsWith("windows") -> WINDOWS
                else -> throw IllegalStateException("Unsupported operating system: ${System.getProperty("os.name")}")
            }
        }
    }
}

enum class CpuArchitecture(val id: String) {
    X86_64("amd64"),
    ARM64("arm64");

    companion object {
        fun current(): CpuArchitecture {
            val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
            return when (architecture) {
                "amd64",
                "x86_64",
                "x64" -> X86_64
                "aarch64",
                "arm64" -> ARM64
                else -> throw IllegalStateException("Unsupported CPU architecture: ${System.getProperty("os.arch")}")
            }
        }
    }
}

/** Downloads and caches versioned kind and cloud-provider-kind executables. */
class BinaryDownloader(
  private val cacheDirectory: Path = defaultCacheDirectory(),
  private val kindDownloadBaseUrl: URI = URI("https://kind.sigs.k8s.io/dl/"),
  private val cloudProviderKindDownloadBaseUrl: URI =
    URI("https://github.com/kubernetes-sigs/cloud-provider-kind/releases/download/"),
  private val httpClient: HttpClient =
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build(),
) {
    fun resolveKindBinary(version: String, configuredBinary: String): String =
      if (version.isBlank()) {
          configuredBinary
      } else {
          downloadKind(version, CpuArchitecture.current(), BinaryOperatingSystem.current()).absolutePath
      }

    fun resolveCloudProviderKindBinary(version: String, configuredBinary: String): String =
      if (version.isBlank()) {
          configuredBinary
      } else {
          downloadCloudProviderKind(version, CpuArchitecture.current(), BinaryOperatingSystem.current()).absolutePath
      }

    fun downloadKind(
      version: String,
      architecture: CpuArchitecture,
      operatingSystem: BinaryOperatingSystem,
    ): File {
        val normalizedVersion = normalizeVersion(version)
        val extension = if (operatingSystem == BinaryOperatingSystem.WINDOWS) ".exe" else ""
        val binaryName = "kind$extension"
        val url = kindDownloadBaseUrl.resolve("v$normalizedVersion/kind-${operatingSystem.id}-${architecture.id}")
        return downloadCached("kind", normalizedVersion, operatingSystem, architecture, binaryName, url)
    }

    fun downloadCloudProviderKind(
      version: String,
      architecture: CpuArchitecture,
      operatingSystem: BinaryOperatingSystem,
    ): File {
        val normalizedVersion = normalizeVersion(version)
        val binaryName = "cloud-provider-kind" + if (operatingSystem == BinaryOperatingSystem.WINDOWS) ".exe" else ""
        val archiveName = "cloud-provider-kind_${normalizedVersion}_${operatingSystem.id}_${architecture.id}.tar.gz"
        val url = cloudProviderKindDownloadBaseUrl.resolve("v$normalizedVersion/$archiveName")
        return downloadCached(
          "cloud-provider-kind",
          normalizedVersion,
          operatingSystem,
          architecture,
          binaryName,
          url,
          archiveBinaryName = binaryName,
        )
    }

    private fun downloadCached(
      artifact: String,
      version: String,
      operatingSystem: BinaryOperatingSystem,
      architecture: CpuArchitecture,
      binaryName: String,
      url: URI,
      archiveBinaryName: String? = null,
    ): File {
        val platform = "${operatingSystem.id}-${architecture.id}"
        val destinationDirectory = cacheDirectory.resolve(artifact).resolve(version).resolve(platform)
        val destination = destinationDirectory.resolve(binaryName)
        if (Files.isRegularFile(destination) && Files.size(destination) > 0) {
            makeExecutable(destination, operatingSystem)
            return destination.toFile()
        }

        Files.createDirectories(destinationDirectory)
        val temporaryBinary = Files.createTempFile(destinationDirectory, "$binaryName-", ".tmp")
        val temporaryArchive =
          if (archiveBinaryName != null) {
              Files.createTempFile(destinationDirectory, "$binaryName-", ".tar.gz")
          } else {
              null
          }
        try {
            downloadTo(url, temporaryArchive ?: temporaryBinary)
            if (temporaryArchive != null) {
                extractBinary(temporaryArchive, archiveBinaryName!!, temporaryBinary)
            }
            if (Files.size(temporaryBinary) == 0L) {
                throw IOException("Downloaded binary from $url was empty")
            }
            makeExecutable(temporaryBinary, operatingSystem)
            try {
                Files.move(
                  temporaryBinary,
                  destination,
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryBinary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryBinary)
            temporaryArchive?.let(Files::deleteIfExists)
        }
        return destination.toFile()
    }

    private fun downloadTo(url: URI, destination: Path) {
        val request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(2)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination))
        if (response.statusCode() !in 200..299) {
            Files.deleteIfExists(destination)
            throw IOException("Download from $url failed with HTTP ${response.statusCode()}")
        }
    }

    private fun extractBinary(archive: Path, binaryName: String, destination: Path) {
        GzipCompressorInputStream(Files.newInputStream(archive)).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (entry.isFile && entry.name.substringAfterLast('/') == binaryName) {
                        Files.newOutputStream(destination).use { tar.copyTo(it) }
                        return
                    }
                    entry = tar.nextEntry
                }
            }
        }
        throw IOException("Archive did not contain '$binaryName'")
    }

    private fun makeExecutable(file: Path, operatingSystem: BinaryOperatingSystem) {
        if (operatingSystem == BinaryOperatingSystem.WINDOWS) {
            return
        }
        try {
            Files.setPosixFilePermissions(
              file,
              setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE,
              ),
            )
        } catch (_: UnsupportedOperationException) {
            file.toFile().setExecutable(true)
        }
    }

    private fun normalizeVersion(version: String): String {
        val normalizedVersion = version.removePrefix("v")
        require(normalizedVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9.+-]*"))) {
            "Invalid binary version: '$version'"
        }
        return normalizedVersion
    }

    companion object {
        private fun defaultCacheDirectory(): Path {
            val gradleUserHome =
              System.getenv("GRADLE_USER_HOME")?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".gradle")
            return gradleUserHome.resolve("caches/gradle-kind-plugin/binaries")
        }
    }
}
