package com.katharia.app

/** Starts a sweep of the saved selection: used by the Quick Settings tile and the app shortcut. */
object SweepLauncher {
    const val ACTION_SWEEP = "com.katharia.app.SWEEP"

    /** Returns false if the accessibility service is off (caller should open the app instead). */
    fun sweepSaved(fromShade: Boolean): Boolean {
        val svc = SweepService.instance ?: return false
        svc.startSaved(fromShade)
        return true
    }
}
