package com.cravewallet.app

import android.app.Application
import com.cravewallet.app.data.Repository
import com.cravewallet.app.reminders.Notifications

class CraveApplication : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        Notifications.createChannel(this)
    }
}
