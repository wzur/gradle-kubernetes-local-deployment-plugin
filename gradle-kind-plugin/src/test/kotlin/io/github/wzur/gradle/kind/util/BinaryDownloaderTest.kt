package io.github.wzur.gradle.kind.util

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

class BinaryDownloaderTest {
    @Test
    fun `downloads kind and cloud provider kind and reuses cached binaries`() {
        val temporaryDirectory = Files.createTempDirectory("binary-downloader-test")
        val kindRequests = AtomicInteger()
        val detectedKindRequests = AtomicInteger()
        val windowsKindRequests = AtomicInteger()
        val providerRequests = AtomicInteger()
        val detectedProviderRequests = AtomicInteger()
        val kindBytes = "kind-binary".toByteArray()
        val detectedKindBytes = "detected-kind-binary".toByteArray()
        val windowsKindBytes = "windows-kind-binary".toByteArray()
        val providerBytes = "provider-binary".toByteArray()
        val detectedProviderBytes = "detected-provider-binary".toByteArray()
        val providerArchive = createTarGz("cloud-provider-kind", providerBytes)
        val currentOs = BinaryOperatingSystem.current()
        val currentArchitecture = CpuArchitecture.current()
        val detectedProviderBinaryName =
          "cloud-provider-kind" + if (currentOs == BinaryOperatingSystem.WINDOWS) ".exe" else ""
        val detectedProviderArchive = createTarGz(detectedProviderBinaryName, detectedProviderBytes)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/kind/v0.30.0/kind-linux-amd64") { exchange ->
            kindRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, kindBytes.size.toLong())
            exchange.responseBody.use { it.write(kindBytes) }
        }
        server.createContext("/kind/v0.30.0/kind-windows-amd64") { exchange ->
            windowsKindRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, windowsKindBytes.size.toLong())
            exchange.responseBody.use { it.write(windowsKindBytes) }
        }
        server.createContext("/kind/v0.30.1/kind-${currentOs.id}-${currentArchitecture.id}") { exchange ->
            detectedKindRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, detectedKindBytes.size.toLong())
            exchange.responseBody.use { it.write(detectedKindBytes) }
        }
        server.createContext("/provider/v0.11.1/cloud-provider-kind_0.11.1_linux_arm64.tar.gz") { exchange ->
            providerRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, providerArchive.size.toLong())
            exchange.responseBody.use { it.write(providerArchive) }
        }
        server.createContext(
          "/provider/v0.11.2/cloud-provider-kind_0.11.2_${currentOs.id}_${currentArchitecture.id}.tar.gz"
        ) { exchange ->
            detectedProviderRequests.incrementAndGet()
            exchange.sendResponseHeaders(200, detectedProviderArchive.size.toLong())
            exchange.responseBody.use { it.write(detectedProviderArchive) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/"
            val downloader =
              BinaryDownloader(
                temporaryDirectory,
                java.net.URI("${baseUrl}kind/"),
                java.net.URI("${baseUrl}provider/"),
              )

            val kind = downloader.downloadKind("v0.30.0", CpuArchitecture.X86_64, BinaryOperatingSystem.LINUX)
            val windowsKind = downloader.downloadKind("0.30.0", CpuArchitecture.X86_64, BinaryOperatingSystem.WINDOWS)
            val detectedKind = Path.of(downloader.resolveKindBinary("v0.30.1", "system-kind"))
            val detectedProvider =
              Path.of(downloader.resolveCloudProviderKindBinary("v0.11.2", "system-cloud-provider-kind"))
            val provider =
              downloader.downloadCloudProviderKind(
                "0.11.1",
                CpuArchitecture.ARM64,
                BinaryOperatingSystem.LINUX,
              )

            assertEquals("kind-binary", Files.readString(kind.toPath()))
            assertEquals("windows-kind-binary", Files.readString(windowsKind.toPath()))
            assertEquals("detected-kind-binary", Files.readString(detectedKind))
            assertEquals("system-kind", downloader.resolveKindBinary("", "system-kind"))
            assertEquals("detected-provider-binary", Files.readString(detectedProvider))
            assertEquals(
              "system-cloud-provider-kind",
              downloader.resolveCloudProviderKindBinary("", "system-cloud-provider-kind"),
            )
            assertTrue(windowsKind.name.endsWith(".exe"))
            assertEquals("provider-binary", Files.readString(provider.toPath()))
            assertTrue(kind.canExecute())
            assertTrue(provider.canExecute())
            assertEquals(kind, downloader.downloadKind("0.30.0", CpuArchitecture.X86_64, BinaryOperatingSystem.LINUX))
            assertEquals(
              provider,
              downloader.downloadCloudProviderKind(
                "v0.11.1",
                CpuArchitecture.ARM64,
                BinaryOperatingSystem.LINUX,
              ),
            )
            assertEquals(1, kindRequests.get())
            assertEquals(1, detectedKindRequests.get())
            assertEquals(1, detectedProviderRequests.get())
            assertEquals(1, windowsKindRequests.get())
            assertEquals(1, providerRequests.get())
        } finally {
            server.stop(0)
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    private fun createTarGz(binaryName: String, bytes: ByteArray): ByteArray {
        val archive = Files.createTempFile("binary-downloader-test", ".tar.gz")
        try {
            GzipCompressorOutputStream(Files.newOutputStream(archive)).use { gzip ->
                TarArchiveOutputStream(gzip).use { tar ->
                    val entry = TarArchiveEntry(binaryName)
                    entry.size = bytes.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
            return Files.readAllBytes(archive)
        } finally {
            Files.deleteIfExists(archive)
        }
    }
}
