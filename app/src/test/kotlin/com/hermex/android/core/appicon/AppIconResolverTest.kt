package com.hermex.android.core.appicon

import com.hermex.android.core.storage.AppIconVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconResolverTest {
    @Test
    fun `LIGHT, DARK, and DISCO always resolve to themselves regardless of dark mode`() {
        assertEquals(ConcreteAppIcon.LIGHT, AppIconResolver.resolve(AppIconVariant.LIGHT, isDarkMode = true))
        assertEquals(ConcreteAppIcon.LIGHT, AppIconResolver.resolve(AppIconVariant.LIGHT, isDarkMode = false))
        assertEquals(ConcreteAppIcon.DARK, AppIconResolver.resolve(AppIconVariant.DARK, isDarkMode = true))
        assertEquals(ConcreteAppIcon.DARK, AppIconResolver.resolve(AppIconVariant.DARK, isDarkMode = false))
        assertEquals(ConcreteAppIcon.DISCO, AppIconResolver.resolve(AppIconVariant.DISCO, isDarkMode = true))
        assertEquals(ConcreteAppIcon.DISCO, AppIconResolver.resolve(AppIconVariant.DISCO, isDarkMode = false))
    }

    @Test
    fun `SYSTEM resolves to DARK in dark mode and LIGHT otherwise`() {
        assertEquals(ConcreteAppIcon.DARK, AppIconResolver.resolve(AppIconVariant.SYSTEM, isDarkMode = true))
        assertEquals(ConcreteAppIcon.LIGHT, AppIconResolver.resolve(AppIconVariant.SYSTEM, isDarkMode = false))
    }
}
