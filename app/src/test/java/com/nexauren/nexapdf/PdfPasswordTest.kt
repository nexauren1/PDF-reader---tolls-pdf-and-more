package com.nexauren.nexapdf

import org.junit.Assert.assertThrows
import org.junit.Test

class PdfPasswordTest {
    @Test fun acceptsEightCharacterPassword() {
        validatePdfPassword("secure123")
    }

    @Test fun rejectsShortPassword() {
        assertThrows(IllegalArgumentException::class.java) { validatePdfPassword("1234567") }
    }

    @Test fun rejectsBlankPassword() {
        assertThrows(IllegalArgumentException::class.java) { validatePdfPassword("        ") }
    }
}
