package com.android.car.carlauncher.recents;

import android.app.ActivityManager;
import android.app.Service;
import android.app.contextualsearch.ContextualSearchConfig;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.android.systemui.shared.recents.ILauncherProxy;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.car.carlauncher.core.platform.QuickStepRecentTasksSession;

/** Bridges CarSystemUI's QuickStep binder callbacks to the XML recents Activity. */
public class CarQuickStepService extends Service {
    private static final String OPEN_RECENT_TASK_ACTION =
            "com.android.car.carlauncher.recents.OPEN_RECENT_TASK_ACTION";
    private ActivityManager mActivityManager;
    private ComponentName mRecentsComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        mActivityManager = getSystemService(ActivityManager.class);
        mRecentsComponent = new ComponentName(this, CarRecentsActivity.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new CarLauncherProxyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        QuickStepRecentTasksSession.terminate();
        return false;
    }

    private boolean isRecentsActivityShown() {
        return mActivityManager.getAppTasks().stream()
                .filter(appTask -> appTask.getTaskInfo().isVisible())
                .map(appTask -> appTask.getTaskInfo().topActivity)
                .anyMatch(component -> component != null
                        && mRecentsComponent.equals(component));
    }

    private void toggleRecents(boolean closeRecents) {
        Intent intent = new Intent().setComponent(mRecentsComponent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (closeRecents) intent.setAction(OPEN_RECENT_TASK_ACTION);
        startActivity(intent);
    }

    private final class CarLauncherProxyBinder extends ILauncherProxy.Stub {
        @Override
        public void onActiveNavBarRegionChanges(Region activeRegion) {}

        @Override
        public void onInitialize(Bundle params) {
            if (params == null) {
                QuickStepRecentTasksSession.terminate();
                return;
            }
            QuickStepRecentTasksSession.initialize(params.getBinder(IRecentTasks.DESCRIPTOR));
        }

        @Override
        public void onOverviewToggle() {
            toggleRecents(isRecentsActivityShown());
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            if (!isRecentsActivityShown()) toggleRecents(false);
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (isRecentsActivityShown()) toggleRecents(true);
        }

        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {}

        @Override
        public void onAssistantVisibilityChanged(float visibility) {}

        @Override
        public void onAssistantOverrideInvoked(int invocationType) {}

        @Override
        public void onSystemUiStateChanged(long stateFlags, int displayId) {}

        @Override
        public void onRotationProposal(int rotation, boolean isValid) {}

        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {}

        @Override
        public void onSystemBarAttributesChanged(int displayId, int behavior) {}

        @Override
        public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {}

        @Override
        public void onNavButtonsDarkIntensityChanged(float darkIntensity) {}

        @Override
        public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {}

        @Override
        public void enterStageSplitFromRunningApp(int displayId, boolean leftOrTop) {}

        @Override
        public void onTaskbarToggled() {}

        @Override
        public void updateWallpaperVisibility(int displayId, boolean visible) {}

        @Override
        public void checkNavBarModes(int displayId) {}

        @Override
        public void finishBarAnimations(int displayId) {}

        @Override
        public void touchAutoDim(int displayId, boolean reset) {}

        @Override
        public void transitionTo(int displayId, int barMode, boolean animate) {}

        @Override
        public void appTransitionPending(boolean pending) {}

        @Override
        public void onUnbind(IRemoteCallback reply) throws RemoteException {
            if (reply != null) reply.sendResult(null);
        }

        @Override
        public void onDisplayAddSystemDecorations(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayRemoveSystemDecorations(int displayId) {}

        @Override
        public void onActionCornerActivated(int action, int displayId) {}

        @Override
        public void invokeContextualSearch(int entryPoint, ContextualSearchConfig config) {}
    }
}
