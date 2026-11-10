package com.filemanagerplus.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StorageNameValidationTest {
    @Test
    fun trimsValidNames() {
        assertEquals("New folder", validateDisplayName("  New folder  "))
    }

    @Test
    fun rejectsEmptyNames() {
        val result = runCatching { validateDisplayName("   ") }
        assertEquals(true, result.isFailure)
    }

    @Test
    fun rejectsPathSeparatorsAndTraversalNames() {
        listOf(".", "..", "a/b", "a\\b").forEach { name ->
            assertEquals("Invalid name should be rejected: $name", true,
                runCatching { validateDisplayName(name) }.isFailure)
        }
    }
}

class StorageRepositorySafetyTest {

    private fun createTempDir(name: String): File {
        return File(System.getProperty("java.io.tmpdir"), name).apply { mkdirs() }
    }

    private fun createTempFile(dir: File, name: String): File {
        return File(dir, name).apply { createNewFile() }
    }

    @Test
    fun isPathWithin_returnsTrueForNestedPath() {
        val repo = StorageRepository(null)
        val parent = createTempDir("parent")
        val child = File(parent, "child")
        child.mkdirs()
        assertEquals(true, repo.isPathWithin(child.absolutePath, parent.absolutePath))
    }

    @Test
    fun isPathWithin_returnsFalseForSiblingPath() {
        val repo = StorageRepository(null)
        val dir1 = createTempDir("dir1")
        val dir2 = createTempDir("dir2")
        assertEquals(false, repo.isPathWithin(dir2.absolutePath, dir1.absolutePath))
    }

    @Test
    fun isPathWithin_returnsFalseForSamePath() {
        val repo = StorageRepository(null)
        val dir = createTempDir("same")
        assertEquals(false, repo.isPathWithin(dir.absolutePath, dir.absolutePath))
    }

    @Test
    fun destinationExists_returnsTrueWhenFileExists() {
        val repo = StorageRepository(null)
        val dir = createTempDir("exists_test")
        createTempFile(dir, "test.txt")
        assertEquals(true, repo.destinationExists(dir.absolutePath, "test.txt"))
    }

    @Test
    fun destinationExists_returnsFalseWhenFileMissing() {
        val repo = StorageRepository(null)
        val dir = createTempDir("missing_test")
        assertEquals(false, repo.destinationExists(dir.absolutePath, "missing.txt"))
    }

    @Test
    fun samePath_returnsTrueForSameFile() {
        val repo = StorageRepository(null)
        val file = createTempFile(createTempDir("samepath"), "file.txt")
        assertEquals(true, repo.samePath(file.absolutePath, file.absolutePath))
    }

    @Test
    fun samePath_returnsFalseForDifferentFiles() {
        val repo = StorageRepository(null)
        val dir = createTempDir("diff_samepath")
        val f1 = createTempFile(dir, "a.txt")
        val f2 = createTempFile(dir, "b.txt")
        assertEquals(false, repo.samePath(f1.absolutePath, f2.absolutePath))
    }
}
