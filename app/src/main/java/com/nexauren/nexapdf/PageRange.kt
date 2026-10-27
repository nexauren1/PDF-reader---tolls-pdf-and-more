package com.nexauren.nexapdf

/** A validated 1-based inclusive range of PDF pages. */
internal data class PageRange private constructor(val firstPage: Int, val lastPage: Int) {
    val pageCount: Int get() = lastPage - firstPage + 1

    companion object {
        fun of(firstPage: Int, lastPage: Int, documentPageCount: Int): PageRange {
            require(documentPageCount > 0) { "The PDF has no pages." }
            require(firstPage in 1..documentPageCount) { "First page must be from 1 to $documentPageCount." }
            require(lastPage in firstPage..documentPageCount) { "Last page must be from the first page to $documentPageCount." }
            return PageRange(firstPage, lastPage)
        }
    }
}
