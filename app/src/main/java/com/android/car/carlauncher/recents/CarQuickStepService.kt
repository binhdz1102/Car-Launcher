package com.android.car.carlauncher.recents

import android.app.ActivityManager
import android.app.Service
import android.app.contextualsearch.ContextualSearchConfig
import android.content.ComponentName
import android.content.Intent
import android.graphics.Region
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import com.android.car.carlauncher.core.platform.QuickStepRecentTasksSession
import com.android.systemui.shared.recents.ILauncherProxy
import com.android.wm.shell.recents.IRecentTasks

/** QuickStep binder adapter. SystemUI callbacks are translated into lifecycle-safe Activity intents. */
class CarQuickStepService : Service() {
    private lateinit var activityManager: ActivityManager
    private lateinit var recentsComponent: ComponentName

    override fun onCreate() {
        super.onCreate()
        activityManager = getSystemService(ActivityManager::class.java)
        recentsComponent = ComponentName(this, CarRecentsActivity::class.java)
    }

    override fun onBind(intent: Intent?): IBinder = CarLauncherProxyBinder()

    override fun onUnbind(intent: Intent?): Boolean {
        QuickStepRecentTasksSession.terminate()
        return false
    }

    private fun isRecentsActivityShown(): Boolean =
        activityManager.appTasks
            .asSequence()
            .mapNotNull { task -> task.taskInfo }
            .filter { taskInfo -> taskInfo.isVisible }
            .mapNotNull { taskInfo -> taskInfo.topActivity }
            .any { component -> component == recentsComponent }

    private fun toggleRecents(closeRecents: Boolean) {
        val intent =
            Intent()
                .setComponent(recentsComponent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (closeRecents) intent.action = CarRecentsActivity.OPEN_RECENT_TASK_ACTION
        startActivity(intent)
    }

    private inner class CarLauncherProxyBinder : ILauncherProxy.Stub() {
        override fun onActiveNavBarRegionChanges(activeRegion: Region?) = Unit

        override fun onInitialize(params: Bundle?) {
            val binder = params?.getBinder(IRecentTasks.DESCRIPTOR)
            QuickStepRecentTasksSession.initialize(binder)
        }

        override fun onOverviewToggle() = toggleRecents(isRecentsActivityShown())

        override fun onOverviewShown(triggeredFromAltTab: Boolean) {
            if (!isRecentsActivityShown()) toggleRecents(closeRecents = false)
        }

        override fun onOverviewHidden(
            triggeredFromAltTab: Boolean,
            triggeredFromHomeKey: Boolean,
        ) {
            if (isRecentsActivityShown()) toggleRecents(closeRecents = true)
        }

        override fun onAssistantAvailable(
            available: Boolean,
            longPressHomeEnabled: Boolean,
        ) = Unit

        override fun onAssistantVisibilityChanged(visibility: Float) = Unit

        override fun onAssistantOverrideInvoked(invocationType: Int) = Unit

        override fun onSystemUiStateChanged(
            stateFlags: Long,
            displayId: Int,
        ) = Unit

        override fun onRotationProposal(
            rotation: Int,
            isValid: Boolean,
        ) = Unit

        override fun disable(
            displayId: Int,
            state1: Int,
            state2: Int,
            animate: Boolean,
        ) = Unit

        override fun onSystemBarAttributesChanged(
            displayId: Int,
            behavior: Int,
        ) = Unit

        override fun onTransitionModeUpdated(
            barMode: Int,
            checkBarModes: Boolean,
        ) = Unit

        override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) = Unit

        override fun onNavigationBarLumaSamplingEnabled(
            displayId: Int,
            enable: Boolean,
        ) = Unit

        override fun enterStageSplitFromRunningApp(
            displayId: Int,
            leftOrTop: Boolean,
        ) = Unit

        override fun onTaskbarToggled() = Unit

        override fun updateWallpaperVisibility(
            displayId: Int,
            visible: Boolean,
        ) = Unit

        override fun checkNavBarModes(displayId: Int) = Unit

        override fun finishBarAnimations(displayId: Int) = Unit

        override fun touchAutoDim(
            displayId: Int,
            reset: Boolean,
        ) = Unit

        override fun transitionTo(
            displayId: Int,
            barMode: Int,
            animate: Boolean,
        ) = Unit

        override fun appTransitionPending(pending: Boolean) = Unit

        override fun onUnbind(reply: IRemoteCallback?) {
            reply?.sendResult(null)
        }

        override fun onDisplayAddSystemDecorations(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayRemoveSystemDecorations(displayId: Int) = Unit

        override fun onActionCornerActivated(
            action: Int,
            displayId: Int,
        ) = Unit

        override fun invokeContextualSearch(
            entryPoint: Int,
            config: ContextualSearchConfig?,
        ) = Unit
    }
}
