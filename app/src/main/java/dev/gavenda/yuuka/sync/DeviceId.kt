package dev.gavenda.yuuka.sync

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "yuuka.device"
private const val KEY_DEVICE_ID = "device_id"

/**
 * This install's own name for itself.
 *
 * It exists for one reason: so the server can leave this device out when it
 * tells the user's other devices that something changed. The device that made
 * the change already has the API's answer, and pushing it back would only make
 * it refetch what it just wrote.
 *
 * It is not an identity and not a secret — it says nothing about who is signed
 * in, and a request carrying someone else's would achieve only that they miss
 * one push. It describes the install rather than the person, so it survives a
 * different user signing in; what does not survive is the FCM registration
 * token, which [PushRegistrar] re-registers per user.
 */
class DeviceId(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val value: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: "android-${UUID.randomUUID()}".also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }
}
