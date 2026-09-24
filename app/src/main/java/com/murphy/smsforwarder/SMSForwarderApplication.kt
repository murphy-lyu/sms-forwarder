package com.murphy.smsforwarder

import android.app.Application
import android.content.Context

class SMSForwarderApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        ForwardStore.pending(this).forEach { ForwardWorker.schedule(this, it.id) }
    }
}
