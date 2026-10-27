package com.nexauren.nexapdf

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextNormalizationTest {
    @Test fun keepsLatinTextAndCleansSmartPunctuation() {
        assertEquals("Ola - \"PDF\" - café", normalizeOcrLine("Ola — “PDF” • café"))
    }

    @Test fun normalizesLineBreaksAndTabs() {
        assertEquals("hello world", normalizeOcrLine("hello\nworld\t"))
    }

    @Test fun replacesUnsupportedCharacters() {
        assertEquals("A?B", normalizeOcrLine("A🙂B"))
    }
}
