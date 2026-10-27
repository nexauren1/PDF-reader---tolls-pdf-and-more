package com.nexauren.nexapdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangeTest {
    @Test fun acceptsSinglePage() {
        val range = PageRange.of(firstPage = 3, lastPage = 3, documentPageCount = 8)
        assertEquals(3, range.firstPage)
        assertEquals(3, range.lastPage)
        assertEquals(1, range.pageCount)
    }

    @Test fun acceptsFullDocumentRange() {
        val range = PageRange.of(firstPage = 1, lastPage = 12, documentPageCount = 12)
        assertEquals(12, range.pageCount)
    }

    @Test fun rejectsZeroOrNegativePageCount() {
        assertThrows(IllegalArgumentException::class.java) { PageRange.of(1, 1, 0) }
    }

    @Test fun rejectsFirstPageOutsideDocument() {
        assertThrows(IllegalArgumentException::class.java) { PageRange.of(0, 1, 5) }
        assertThrows(IllegalArgumentException::class.java) { PageRange.of(6, 6, 5) }
    }

    @Test fun rejectsEndBeforeStartOrBeyondDocument() {
        assertThrows(IllegalArgumentException::class.java) { PageRange.of(4, 3, 5) }
        assertThrows(IllegalArgumentException::class.java) { PageRange.of(1, 6, 5) }
    }
}
