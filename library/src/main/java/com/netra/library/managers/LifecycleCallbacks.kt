package com.netra.library.managers

import android.app.Activity
import android.app.Application
import android.os.Bundle

class LifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityDestroyed(activity: Activity) {
        CancelRequestManager.cancelAll()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) {
    }
}