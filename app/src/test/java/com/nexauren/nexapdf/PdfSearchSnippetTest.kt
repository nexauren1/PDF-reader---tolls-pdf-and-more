package com.nexauren.nexapdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSearchSnippetTest {
    @Test fun findsCaseInsensitiveMatchAndReturnsContext() {
        val snippet = findPdfSearchSnippet("Introduction\nProject details follow here.", "PROJECT")
        assertTrue(snippet!!.contains("Project"))
    }

    @Test fun returnsNullWhenTextDoesNotContainQuery() {
        assertNull(findPdfSearchSnippet("A document page", "missing"))
    }

    @Test fun ignoresBlankQueries() {
        assertNull(findPdfSearchSnippet("A document page", "  "))
    }

    @Test fun normalizesWhitespaceAroundMatch() {
        assertEquals("hello world", findPdfSearchSnippet("hello\nworld", "world"))
    }
}
