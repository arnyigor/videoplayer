package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class CatalogArchiveReaderTest {
    @TempDir
    lateinit var directory: File

    private fun shard(name: String, json: String): File = File(directory, name).apply { writeText(json) }
    private fun valid(title: String) = """{"movies":[{"title":"$title","pageUrl":"films/1"}]}"""

    @Test
    fun `valid shards are merged before importing`() {
        val result = readCatalogFiles(listOf(shard("data.json", valid("One")), shard("data_1.json", valid("Two"))), false)
        assertEquals(listOf("One", "Two"), result.map { it.title })
    }

    @Test
    fun `one malformed shard rejects entire package instead of skipping it`() {
        val files = listOf(shard("data.json", valid("One")), shard("data_1.json", "not json"))
        assertThrows(IOException::class.java) { readCatalogFiles(files, false) }
    }

    @Test
    fun `missing null empty or invalid movies reject package`() {
        for (json in listOf("null", "{}", "{\"movies\":null}", "{\"movies\":[]}",
            "{\"movies\":[null]}", "{\"movies\":[{}]}", "{\"movies\":[{\"title\":\"One\"}]}")) {
            assertThrows(IOException::class.java) { readCatalogFiles(listOf(shard("data.json", json)), false) }
        }
    }

    @Test
    fun `period update selects only data zero when available`() {
        val files = listOf(shard("data.json", valid("Full")), shard("data_0.json", valid("Partial")))
        assertEquals(listOf("Partial"), readCatalogFiles(files, true).map { it.title })
    }

    @Test
    fun `empty package rejects import`() {
        assertThrows(IOException::class.java) { readCatalogFiles(emptyList(), false) }
    }
}
