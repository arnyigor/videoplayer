package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractionTest {
    @TempDir
    lateinit var directory: File

    private fun archive(vararg entries: Pair<String, String>): File =
        File(directory, "update.zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun `nested files are extracted without explicit directory entries`() {
        val destination = File(directory, "output")
        archive("nested/data.json" to "[]").unzip(destination.path)
        assertEquals("[]", File(destination, "nested/data.json").readText())
    }

    @Test
    fun `traversal is rejected before any file is written`() {
        for (name in listOf("../outside.json", "..\\outside.json", "/outside.json", "C:/outside.json")) {
            val destination = File(directory, "output")
            assertThrows(IOException::class.java) {
                archive("data.json" to "[]", name to "bad").unzip(destination.path)
            }
            assertFalse(File(directory, "outside.json").exists())
            assertFalse(File(destination, "data.json").exists())
        }
    }

    @Test
    fun `canonical duplicate paths are rejected`() {
        assertThrows(IOException::class.java) {
            archive("data.json" to "[]", "nested/../data.json" to "bad")
                .unzip(File(directory, "output").path)
        }
    }

    @Test
    fun `entry count is limited`() {
        assertThrows(IOException::class.java) {
            archive("one.json" to "1", "two.json" to "2")
                .extractZip(File(directory, "output"), maxEntries = 1)
        }
    }

    @Test
    fun `uncompressed size is limited across entries`() {
        assertThrows(IOException::class.java) {
            archive("one.json" to "123", "two.json" to "456")
                .extractZip(File(directory, "output"), maxBytes = 5)
        }
    }

    @Test
    fun `catalog import uses only files from the current archive`() {
        val stale = File(directory, "stale.json").apply { writeText("old") }
        val zip = archive("data.json" to "[]")
        val files = extractCatalogData(zip, directory, ".json")
        try {
            assertEquals(listOf("data.json"), files.map { it.name })
            assertNotEquals(directory.canonicalFile, requireNotNull(files.single().parentFile).canonicalFile)
            assertEquals("old", stale.readText())
            assertTrue(zip.exists(), "Keep the archive until the caller finishes importing")
        } finally {
            requireNotNull(files.single().parentFile).deleteRecursively()
        }
    }

    @Test
    fun `invalid catalog extraction cleans its temporary directory`() {
        val zip = archive("data.json" to "")
        assertThrows(IOException::class.java) { extractCatalogData(zip, directory, ".json") }
        assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("catalog-") })
    }

    @Test
    fun `archive without catalog files is rejected and cleaned`() {
        val zip = archive("readme.txt" to "not a catalog")
        assertThrows(IOException::class.java) { extractCatalogData(zip, directory, ".json") }
        assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("catalog-") })
    }
}
