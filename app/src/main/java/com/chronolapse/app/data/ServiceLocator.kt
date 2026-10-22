package com.chronolapse.app.data

import android.content.Context

/** Minimal service locator: one app context for the singletons. */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() = appContext ?: error("ServiceLocator not initialized")

    val settings: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }
}
