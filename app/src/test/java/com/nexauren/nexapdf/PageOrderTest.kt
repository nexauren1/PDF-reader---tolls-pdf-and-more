package com.nexauren.nexapdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageOrderTest {
    @Test fun parsesACompletePageOrder() {
        assertEquals(listOf(3, 1, 2), parsePageOrder("3, 1, 2", 3))
    }

    @Test fun rejectsDuplicatePageNumbers() {
        assertThrows(IllegalArgumentException::class.java) { parsePageOrder("1,1,3", 3) }
    }

    @Test fun rejectsMissingPages() {
        assertThrows(IllegalArgumentException::class.java) { parsePageOrder("1,3", 3) }
    }

    @Test fun rejectsOutOfRangePageNumbers() {
        assertThrows(IllegalArgumentException::class.java) { parsePageOrder("1,2,4", 3) }
    }

    @Test fun rejectsNonNumericPageNumbers() {
        assertThrows(IllegalArgumentException::class.java) { parsePageOrder("1,abc,3", 3) }
    }
}
