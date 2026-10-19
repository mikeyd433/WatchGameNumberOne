package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.format.BigNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class BigNumberTest {
    @Test fun smallNumbersAreWhole() {
        assertEquals("0", BigNumber.format(0.0))
        assertEquals("7", BigNumber.format(7.9))
        assertEquals("999", BigNumber.format(999.99))
    }

    @Test fun suffixes() {
        assertEquals("1K", BigNumber.format(1000.0))
        assertEquals("1.2K", BigNumber.format(1_299.0))
        assertEquals("12.3K", BigNumber.format(12_345.0))
        assertEquals("123K", BigNumber.format(123_456.0))
        assertEquals("999K", BigNumber.format(999_999.0))
        assertEquals("1M", BigNumber.format(1_000_000.0))
        assertEquals("3.4M", BigNumber.format(3_400_000.0))
        assertEquals("1B", BigNumber.format(1e9))
        assertEquals("999T", BigNumber.format(999.9e12))
    }

    @Test fun scientificPastTrillions() {
        assertEquals("1e15", BigNumber.format(1e15))
        assertEquals("1.2e15", BigNumber.format(1.25e15))
        assertEquals("9.9e20", BigNumber.format(9.99e20))
    }

    @Test fun negative() {
        assertEquals("-1.5K", BigNumber.format(-1_500.0))
    }
}
