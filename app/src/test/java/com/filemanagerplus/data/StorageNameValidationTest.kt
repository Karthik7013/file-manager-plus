package com.filemanagerplus.data

import org.junit.Assert.assertEquals
import org.junit.Test

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
