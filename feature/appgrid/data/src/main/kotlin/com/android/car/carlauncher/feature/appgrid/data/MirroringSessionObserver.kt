package com.android.car.carlauncher.feature.appgrid.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.android.car.carlauncher.core.platform.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

data class MirroringSession(
    val packageName: String = "",
    val redirectIntentUri: String? = null,
)

interface MirroringSessionObserver {
    val sessions: Flow<MirroringSession>
}

/**
 * Adapts Control Center's Messenger callback to Flow. Handler is confined to this mandatory
 * Android callback adapter; feature state is Flow/structured-concurrency based.
 */
@Singleton
class AndroidMirroringSessionObserver
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : MirroringSessionObserver {
        override val sessions: Flow<MirroringSession> =
            callbackFlow {
                trySend(MirroringSession())
                val receiver = Messenger(MirroringHandler(this))
                val connection = MirroringServiceConnection(receiver, this)
                val intent = Intent().setComponent(CONTROL_CENTER_COMPONENT)
                val serviceAvailable = context.packageManager.resolveService(intent, 0) != null
                if (serviceAvailable) {
                    runCatching {
                        context.bindService(
                            intent,
                            connection,
                            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
                        )
                    }.onFailure { throwable ->
                        Timber.tag(TAG).w(throwable, "Unable to bind Control Center mirroring service")
                    }
                }
                awaitClose { connection.close(context) }
            }.conflate()
                .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

        private class MirroringHandler(
            private val producer: kotlinx.coroutines.channels.ProducerScope<MirroringSession>,
        ) : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (message.what == MESSAGE_MIRRORING_SESSION) {
                    val payload = message.obj as? Bundle
                    val packageName = payload?.getString(KEY_MIRRORING_PACKAGE).orEmpty()
                    if (packageName.isBlank()) {
                        producer.trySend(MirroringSession())
                    } else {
                        producer.trySend(
                            MirroringSession(
                                packageName = packageName,
                                redirectIntentUri = payload?.canonicalRedirectUri(),
                            ),
                        )
                    }
                }
            }

            private fun Bundle.canonicalRedirectUri(): String? =
                runCatching {
                    Intent
                        .parseUri(getString(KEY_REDIRECT_URI), Intent.URI_INTENT_SCHEME)
                        .toUri(Intent.URI_INTENT_SCHEME)
                }.onFailure { throwable ->
                    if (throwable !is URISyntaxException) {
                        Timber.tag(TAG).w(throwable, "Invalid mirroring redirect")
                    }
                }.getOrNull()
        }

        private class MirroringServiceConnection(
            private val receiver: Messenger,
            private val producer: kotlinx.coroutines.channels.ProducerScope<MirroringSession>,
        ) : ServiceConnection {
            private var service: Messenger? = null

            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                service = Messenger(binder)
                runCatching {
                    Message.obtain(null, MESSAGE_REGISTER).apply { replyTo = receiver }.also(service!!::send)
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "Unable to register mirroring callback")
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                producer.trySend(MirroringSession())
                service = null
            }

            fun close(context: Context) {
                service?.let { remote ->
                    runCatching {
                        Message.obtain(null, MESSAGE_UNREGISTER).apply { replyTo = receiver }.also(remote::send)
                    }
                }
                service = null
                runCatching { context.unbindService(this) }
            }
        }

        private companion object {
            const val TAG = "CarLauncher.AppGrid.Mirroring"
            const val MESSAGE_REGISTER = 1
            const val MESSAGE_UNREGISTER = 2
            const val MESSAGE_MIRRORING_SESSION = 3
            const val KEY_MIRRORING_PACKAGE = "msg_mirroring_pkg_name_key"
            const val KEY_REDIRECT_URI = "msg_mirroring_redirect_uri_key"
            val CONTROL_CENTER_COMPONENT =
                ComponentName(
                    "com.android.car.multidisplay.controlcenter",
                    "com.android.car.multidisplay.controlcenter.service.ControlCenterService",
                )
        }
    }
