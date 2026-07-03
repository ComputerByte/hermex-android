package com.hermex.android.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderLogoColorTest {
    @Test
    fun `a valid stored name resolves to the matching color`() {
        assertEquals(HeaderLogoColor.BLUE, HeaderLogoColor.fromStoredNameOrDefault("BLUE"))
        assertEquals(HeaderLogoColor.MONO, HeaderLogoColor.fromStoredNameOrDefault("MONO"))
    }

    @Test
    fun `a null stored name falls back to DEFAULT`() {
        assertEquals(HeaderLogoColor.DEFAULT, HeaderLogoColor.fromStoredNameOrDefault(null))
    }

    @Test
    fun `an unrecognized stored name falls back to DEFAULT rather than crashing`() {
        assertEquals(HeaderLogoColor.DEFAULT, HeaderLogoColor.fromStoredNameOrDefault("TEAL"))
        assertEquals(HeaderLogoColor.DEFAULT, HeaderLogoColor.fromStoredNameOrDefault(""))
        assertEquals(HeaderLogoColor.DEFAULT, HeaderLogoColor.fromStoredNameOrDefault("blue")) // case-sensitive by design
    }
}
