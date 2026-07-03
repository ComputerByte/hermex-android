package com.hermex.android.core.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The one PackageManager operation [AppIconSwitcher] needs -- pulled behind an interface so the
 * switcher's enable-one-disable-the-rest orchestration logic is unit-testable with a fake, since
 * `PackageManager`/`ComponentName` aren't usable in this project's plain-JVM test source set
 * (no Robolectric). */
interface AppIconAliasWriter {
    fun setEnabled(alias: ConcreteAppIcon, enabled: Boolean)
}

/** Default used wherever no real writer is wired in (most unit tests) -- every write is a
 * no-op, mirroring [com.hermex.android.core.storage.NoOpCustomHeadersStore]. */
object NoOpAppIconAliasWriter : AppIconAliasWriter {
    override fun setEnabled(alias: ConcreteAppIcon, enabled: Boolean) = Unit
}

/**
 * Real implementation. [PackageManager.DONT_KILL_APP] is used so flipping the enabled alias
 * doesn't kill and restart the app's process -- but expect the on-screen change to be imperfect:
 * some launchers (notably some OEM skins) don't refresh a changed icon immediately, and some
 * briefly remove and re-add the icon from the home screen/app drawer while they do.
 */
class PackageManagerAppIconAliasWriter(private val context: Context) : AppIconAliasWriter {
    override fun setEnabled(alias: ConcreteAppIcon, enabled: Boolean) {
        val component = ComponentName(context.packageName, "${context.packageName}.${alias.aliasSimpleName}")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
