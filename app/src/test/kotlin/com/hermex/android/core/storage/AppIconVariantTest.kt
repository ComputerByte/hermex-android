package com.hermex.android.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconVariantTest {
    @Test
    fun `a valid stored name resolves to the matching variant`() {
        assertEquals(AppIconVariant.LIGHT, AppIconVariant.fromStoredNameOrDefault("LIGHT"))
        assertEquals(AppIconVariant.DISCO, AppIconVariant.fromStoredNameOrDefault("DISCO"))
    }

    @Test
    fun `a null stored name falls back to SYSTEM`() {
        assertEquals(AppIconVariant.SYSTEM, AppIconVariant.fromStoredNameOrDefault(null))
    }

    @Test
    fun `an unrecognized stored name falls back to SYSTEM rather than crashing`() {
        assertEquals(AppIconVariant.SYSTEM, AppIconVariant.fromStoredNameOrDefault("NEON"))
        assertEquals(AppIconVariant.SYSTEM, AppIconVariant.fromStoredNameOrDefault(""))
        assertEquals(AppIconVariant.SYSTEM, AppIconVariant.fromStoredNameOrDefault("light")) // case-sensitive by design
    }
}
