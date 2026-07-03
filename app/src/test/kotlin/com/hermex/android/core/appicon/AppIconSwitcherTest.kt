package com.hermex.android.core.appicon

import com.hermex.android.core.storage.AppIconVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAppIconAliasWriter : AppIconAliasWriter {
    val enabledState = mutableMapOf<ConcreteAppIcon, Boolean>()

    override fun setEnabled(alias: ConcreteAppIcon, enabled: Boolean) {
        enabledState[alias] = enabled
    }
}

class AppIconSwitcherTest {
    @Test
    fun `applyVariant enables the target alias and disables the other two`() {
        val writer = FakeAppIconAliasWriter()
        val switcher = AppIconSwitcher(writer, isDarkModeProvider = { false })

        switcher.applyVariant(AppIconVariant.DARK)

        assertTrue(writer.enabledState[ConcreteAppIcon.DARK] == true)
        assertFalse(writer.enabledState[ConcreteAppIcon.LIGHT] == true)
        assertFalse(writer.enabledState[ConcreteAppIcon.DISCO] == true)
    }

    @Test
    fun `SYSTEM resolves through isDarkModeProvider at call time`() {
        val writer = FakeAppIconAliasWriter()
        var isDark = false
        val switcher = AppIconSwitcher(writer, isDarkModeProvider = { isDark })

        switcher.applyVariant(AppIconVariant.SYSTEM)
        assertTrue(writer.enabledState[ConcreteAppIcon.LIGHT] == true)
        assertFalse(writer.enabledState[ConcreteAppIcon.DARK] == true)

        isDark = true
        switcher.applyVariant(AppIconVariant.SYSTEM)
        assertTrue(writer.enabledState[ConcreteAppIcon.DARK] == true)
        assertFalse(writer.enabledState[ConcreteAppIcon.LIGHT] == true)
    }

    @Test
    fun `switching between variants disables the previously-active alias`() {
        val writer = FakeAppIconAliasWriter()
        val switcher = AppIconSwitcher(writer, isDarkModeProvider = { false })

        switcher.applyVariant(AppIconVariant.DISCO)
        assertTrue(writer.enabledState[ConcreteAppIcon.DISCO] == true)

        switcher.applyVariant(AppIconVariant.LIGHT)
        assertTrue(writer.enabledState[ConcreteAppIcon.LIGHT] == true)
        assertFalse("switching away from Disco must disable it", writer.enabledState[ConcreteAppIcon.DISCO] == true)
    }

    @Test
    fun `resolvedAlias reports the target without writing anything`() {
        val writer = FakeAppIconAliasWriter()
        val switcher = AppIconSwitcher(writer, isDarkModeProvider = { true })

        val resolved = switcher.resolvedAlias(AppIconVariant.SYSTEM)

        assertEquals(ConcreteAppIcon.DARK, resolved)
        assertTrue(writer.enabledState.isEmpty())
    }
}
