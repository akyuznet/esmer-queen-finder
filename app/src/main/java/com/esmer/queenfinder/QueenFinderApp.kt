package com.esmer.queenfinder

import android.app.Application
import com.esmer.queenfinder.alert.Alerter
import com.esmer.queenfinder.data.SettingsStore

/** Application-wide singletons. Small enough that no DI framework is needed. */
class QueenFinderApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var alerter: Alerter
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        alerter = Alerter(this)
    }
}
