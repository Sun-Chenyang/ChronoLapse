package com.chronolapse.app

import android.app.Application
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.rest.RestServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChronoApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // keep the REST server in sync with the setting, whatever changed it
        appScope.launch {
            ServiceLocator.settings.flow
                .map { it.restEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled && !RestServer.isRunning()) RestServer.start(this@ChronoApp)
                    else if (!enabled && RestServer.isRunning()) RestServer.stop(this@ChronoApp)
                }
        }
    }
}
