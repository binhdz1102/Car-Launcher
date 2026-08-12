package com.android.car.carlauncher.homescreen.audio.telecom

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.android.car.carlauncher.feature.media.data.CallStateStore
import com.android.car.carlauncher.feature.media.data.InCallControlGateway
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Manifest-compatible Telecom endpoint. It preserves the local bind contract from the baseline
 * while forwarding framework callbacks to the coroutine/Flow media feature boundary.
 */
@AndroidEntryPoint
class InCallServiceImpl : InCallService() {
    @Inject lateinit var callStateStore: CallStateStore

    @Inject lateinit var controlGateway: InCallControlGateway

    private val callbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        controlGateway.attach(::setMuted)
    }

    override fun onDestroy() {
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callbacks.clear()
        controlGateway.detach()
        callStateStore.clear()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callback =
            object : Call.Callback() {
                override fun onStateChanged(
                    changedCall: Call,
                    state: Int,
                ) {
                    callStateStore.onCallChanged(changedCall)
                }

                override fun onDetailsChanged(
                    changedCall: Call,
                    details: Call.Details,
                ) {
                    callStateStore.onCallChanged(changedCall)
                }
            }
        callbacks[call] = callback
        call.registerCallback(callback)
        callStateStore.onCallAdded(call)
        Timber.tag(TAG).d("Call added")
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let(call::unregisterCallback)
        callStateStore.onCallRemoved(call)
        super.onCallRemoved(call)
        Timber.tag(TAG).d("Call removed")
    }

    @Deprecated("Android Telecom callback; retained for AAOS source compatibility.")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callStateStore.onCallAudioStateChanged(audioState)
    }

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_BIND) {
            LocalBinder()
        } else {
            super.onBind(intent)
        }

    override fun onUnbind(intent: Intent): Boolean =
        if (intent.action == ACTION_LOCAL_BIND) {
            false
        } else {
            super.onUnbind(intent)
        }

    inner class LocalBinder : Binder() {
        fun getService(): InCallServiceImpl? = serviceIfLocal()
    }

    private fun isSameProcessCall(): Boolean = Binder.getCallingPid() == Process.myPid()

    private fun serviceIfLocal(): InCallServiceImpl? =
        if (isSameProcessCall()) {
            this
        } else {
            null
        }

    companion object {
        const val ACTION_LOCAL_BIND = "local_bind"
        private const val TAG = "CarLauncher.InCallService"
    }
}
