package com.novelarrow.reader.data.util

import java.util.Comparator

/**
 * Natural alphanumeric comparator (e.g. Chapter 1, Chapter 2, ... Chapter 10).
 * Matches the source app's naturalCompare logic.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val nza: Int
        val nzb: Int
        var ca: Char
        var cb: Char

        while (true) {
            var nzaCount = 0
            var nzbCount = 0

            ca = charAt(a, ia)
            cb = charAt(b, ib)

            // skip leading spaces
            while (Character.isSpaceChar(ca) || ca == ' ') {
                ia++
                ca = charAt(a, ia)
            }
            while (Character.isSpaceChar(cb) || cb == ' ') {
                ib++
                cb = charAt(b, ib)
            }

            // process digit run
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                if (ca == '0') {
                    while (charAt(a, ia) == '0') {
                        nzaCount++
                        ia++
                    }
                }
                if (cb == '0') {
                    while (charAt(b, ib) == '0') {
                        nzbCount++
                        ib++
                    }
                }

                var bias = 0
                while (true) {
                    ca = charAt(a, ia)
                    cb = charAt(b, ib)

                    val aIsDigit = Character.isDigit(ca)
                    val bIsDigit = Character.isDigit(cb)

                    if (!aIsDigit && !bIsDigit) {
                        break
                    } else if (!aIsDigit) {
                        return -1
                    } else if (!bIsDigit) {
                        return 1
                    } else if (ca < cb) {
                        if (bias == 0) bias = -1
                    } else if (ca > cb) {
                        if (bias == 0) bias = 1
                    }
                    ia++
                    ib++
                }

                if (bias != 0) return bias
                if (nzaCount != nzbCount) return nzaCount - nzbCount
            }

            if (ca == 0.toChar() && cb == 0.toChar()) {
                return a.length - b.length
            }

            if (ca.lowercaseChar() < cb.lowercaseChar()) {
                return -1
            } else if (ca.lowercaseChar() > cb.lowercaseChar()) {
                return 1
            }

            ia++
            ib++
        }
    }

    private fun charAt(s: String, i: Int): Char {
        return if (i >= s.length) 0.toChar() else s[i]
    }
}
