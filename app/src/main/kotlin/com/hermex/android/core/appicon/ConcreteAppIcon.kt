package com.hermex.android.core.appicon

/** The launcher aliases actually declared in AndroidManifest.xml -- unlike
 * [com.hermex.android.core.storage.AppIconVariant], every one of these has a real icon and a real
 * `activity-alias` component; there's no `SYSTEM` entry here because Android has nothing to point
 * it at directly. [aliasSimpleName] is the alias's simple class name as declared in the manifest
 * (`android:name=".LauncherLight"` etc.), used to build the fully-qualified [android.content.ComponentName]
 * at runtime. */
enum class ConcreteAppIcon(val aliasSimpleName: String) {
    LIGHT("LauncherLight"),
    DARK("LauncherDark"),
    DISCO("LauncherDisco"),
}
