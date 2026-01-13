package com.browserselector.android

import android.app.Application
import com.browserselector.android.data.BrowserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for BrowserSelector.
 * Initializes the repository and performs first-run setup.
 */
class BrowserSelectorApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val repository: BrowserRepository by lazy {
        BrowserRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize repository and detect browsers on first run
        applicationScope.launch(Dispatchers.IO) {
            repository.initializeIfNeeded()
        }
    }
}
