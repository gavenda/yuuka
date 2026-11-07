package dev.gavenda.yuuka.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Where Android answers an update's install session. It nearly always wants the person first, so it hands back
 * its confirmation screen to open; on success the app is replaced and nothing here runs again. Being called off
 * on that screen is the person's choice and says nothing; any other failure brings the dialog back.
 */
class UpdateInstallReceiver : BroadcastReceiver(), KoinComponent {
    private val updater: AppUpdater by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> updater.installFailed()
        }
    }
}
