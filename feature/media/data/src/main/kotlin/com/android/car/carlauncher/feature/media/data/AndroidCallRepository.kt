package com.android.car.carlauncher.feature.media.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import com.android.car.carlauncher.feature.media.domain.CallCard
import com.android.car.carlauncher.feature.media.domain.CallCardState
import com.android.car.carlauncher.feature.media.domain.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state bridge between the manifest-bound InCallService and the Home card. Telecom
 * callbacks are reduced in a structured coroutine scope and never expose [Call] outside data.
 */
@Singleton
class CallStateStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val events =
            MutableSharedFlow<CallEvent>(
                extraBufferCapacity = EVENT_BUFFER_SIZE,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        private val trackedCalls = LinkedHashMap<Call, TrackedCall>()
        private val mutableActiveCall = MutableStateFlow<CallCard?>(null)

        private var muted = false
        private var currentCall: Call? = null

        val activeCall: StateFlow<CallCard?> = mutableActiveCall.asStateFlow()

        init {
            scope.launch {
                events.collect(::reduce)
            }
        }

        fun onCallAdded(call: Call) {
            events.tryEmit(CallEvent.Added(call))
        }

        fun onCallChanged(call: Call) {
            events.tryEmit(CallEvent.Changed(call))
        }

        fun onCallRemoved(call: Call) {
            events.tryEmit(CallEvent.Removed(call))
        }

        fun onCallAudioStateChanged(audioState: CallAudioState) {
            events.tryEmit(CallEvent.AudioStateChanged(audioState.isMuted))
        }

        fun endCurrentCall() {
            currentCall?.disconnect()
        }

        fun clear() {
            events.tryEmit(CallEvent.Cleared)
        }

        private fun reduce(event: CallEvent) {
            when (event) {
                is CallEvent.Added -> trackedCalls[event.call] = TrackedCall(event.call)
                is CallEvent.Changed -> {
                    if (event.call in trackedCalls) trackedCalls[event.call] = TrackedCall(event.call)
                }
                is CallEvent.Removed -> trackedCalls.remove(event.call)
                is CallEvent.AudioStateChanged -> muted = event.isMuted
                CallEvent.Cleared -> {
                    trackedCalls.clear()
                    muted = false
                }
            }
            currentCall =
                trackedCalls.values
                    .sortedBy(TrackedCall::priority)
                    .firstOrNull()
                    ?.call
            mutableActiveCall.value = currentCall?.toCard(muted)
        }

        @Suppress("DEPRECATION")
        private fun Call.toCard(isMuted: Boolean): CallCard {
            val details = details
            val packageName = details.accountHandle?.componentName?.packageName
            val appLabel = packageName?.let(context::applicationLabel) ?: DEFAULT_PHONE_LABEL
            val caller = details.handle?.schemeSpecificPart.orEmpty()
            val connectTimeMs = details.connectTimeMillis
            val elapsedConnectionTime =
                connectTimeMs
                    .takeIf { it > 0L }
                    ?.let { timestamp ->
                        (SystemClock.elapsedRealtime() - System.currentTimeMillis() + timestamp)
                            .coerceAtLeast(0L)
                    }
            return CallCard(
                id = System.identityHashCode(this).toString(),
                packageName = packageName,
                appLabel = appLabel,
                caller = caller,
                state = state.toCallCardState(),
                isMuted = isMuted,
                connectedElapsedRealtimeMs = elapsedConnectionTime,
            )
        }

        private data class TrackedCall(
            val call: Call,
        ) {
            @Suppress("DEPRECATION")
            val priority: Int
                get() = call.state.priority()
        }

        private sealed interface CallEvent {
            data class Added(
                val call: Call,
            ) : CallEvent

            data class Changed(
                val call: Call,
            ) : CallEvent

            data class Removed(
                val call: Call,
            ) : CallEvent

            data class AudioStateChanged(
                val isMuted: Boolean,
            ) : CallEvent

            data object Cleared : CallEvent
        }

        private companion object {
            const val DEFAULT_PHONE_LABEL = "Phone"
            const val EVENT_BUFFER_SIZE = 16
        }
    }

/** Service-owned control callback, attached only while Telecom binds the launcher service. */
@Singleton
class InCallControlGateway
    @Inject
    constructor() {
        private var setMuted: ((Boolean) -> Unit)? = null

        @Synchronized
        fun attach(setMuted: (Boolean) -> Unit) {
            this.setMuted = setMuted
        }

        @Synchronized
        fun detach() {
            setMuted = null
        }

        @Synchronized
        fun setMuted(isMuted: Boolean): Boolean {
            val callback = setMuted ?: return false
            callback(isMuted)
            return true
        }
    }

@Singleton
@SuppressLint("MissingPermission")
class AndroidCallRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val callStateStore: CallStateStore,
        private val controlGateway: InCallControlGateway,
    ) : CallRepository {
        override val activeCall: StateFlow<CallCard?> = callStateStore.activeCall

        override fun toggleMute() {
            val targetMuted = !(activeCall.value?.isMuted ?: return)
            if (!controlGateway.setMuted(targetMuted)) {
                Timber.tag(TAG).w("In-call service is unavailable; cannot change mute state")
            }
        }

        override fun endCall() {
            callStateStore.endCurrentCall()
        }

        override fun openDialpad() {
            runCatching {
                context.getSystemService(TelecomManager::class.java).showInCallScreen(false)
            }.recoverCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Unable to open in-call dialpad") }
        }

        private companion object {
            const val TAG = "CarLauncher.CallRepository"
        }
    }

private fun Int.toCallCardState(): CallCardState =
    when (this) {
        Call.STATE_RINGING -> CallCardState.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING -> CallCardState.DIALING
        Call.STATE_HOLDING -> CallCardState.HOLDING
        Call.STATE_DISCONNECTING -> CallCardState.DISCONNECTING
        else -> CallCardState.ACTIVE
    }

private fun Int.priority(): Int = toCallCardState().ordinal

private fun Context.applicationLabel(packageName: String): String =
    runCatching {
        packageManager
            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    }.getOrDefault(packageName)
