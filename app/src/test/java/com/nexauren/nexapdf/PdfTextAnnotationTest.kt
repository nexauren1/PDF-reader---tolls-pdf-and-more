package com.nexauren.nexapdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfTextAnnotationTest {
    @Test fun normalizesMultilineTextToSingleLine() {
        assertEquals("Nota para revisão", normalizePdfAnnotationText("  Nota\npara\trevisão  "))
    }

    @Test fun normalizesSmartPunctuationForBuiltInFont() {
        assertEquals("'Olá' - \"PDF\"", normalizePdfAnnotationText("‘Olá’ — “PDF”"))
    }

    @Test fun rejectsBlankText() {
        assertThrows(IllegalArgumentException::class.java) { normalizePdfAnnotationText(" \n\t ") }
    }

    @Test fun limitsAnnotationTextToOneHundredEightyCharacters() {
        val actual = normalizePdfAnnotationText("x".repeat(220))
        assertEquals(180, actual.length)
    }
}
