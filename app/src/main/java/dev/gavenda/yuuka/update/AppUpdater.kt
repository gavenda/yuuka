package dev.gavenda.yuuka.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import androidx.core.net.toUri
import dev.gavenda.yuuka.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** A release on GitHub newer than the one running. */
data class AppRelease(val version: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    /** Nothing to offer: not checked yet, up to date, put off until the next open, or handed to the installer. */
    data object Idle : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] is 0..1, or null while the size is not known. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Failed(val release: AppRelease) : UpdateState
}

/**
 * Updates the app from its GitHub releases, since it is not installed from a store. A fresh open asks GitHub for
 * the latest release; one newer than this build is offered in a dialog, and taking it streams the release's APK
 * into a [PackageInstaller] session, which hands over to Android's own confirmation screen. What Android answers
 * comes back through [UpdateInstallReceiver].
 *
 * GitHub is called with a client of its own: the API's client attaches the person's access token, which must
 * never leave for another host.
 */
class AppUpdater(private val context: Context) {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checked = AtomicBoolean(false)
    private var download: Job? = null
    /** The release last handed to the installer, for when Android answers that it could not install it. */
    private var installing: AppRelease? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Looks for a newer release, once per process, so the dialog comes with a fresh open and not every time the
     * app is brought back. A debug build never looks: it is signed with another key, and Android would refuse
     * to put a release over it.
     */
    fun checkOnce() {
        if (BuildConfig.DEBUG || !checked.compareAndSet(false, true)) return
        scope.launch {
            val release = runCatching { latestRelease() }.getOrNull() ?: return@launch
            if (isNewerVersion(release.version, BuildConfig.VERSION_NAME)) _state.value = UpdateState.Available(release)
        }
    }

    /** Whether Android will let this app install one. The person allows it once, from the screen [installPermissionIntent] opens. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Downloads [release] into an install session and commits it, which brings up Android's confirmation. */
    fun install(release: AppRelease) {
        if (download?.isActive == true) return
        installing = release
        _state.value = UpdateState.Downloading(release, null)
        download = scope.launch {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(release.size)
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    write(release, session)
                    session.commit(statusReceiver(sessionId))
                }
                _state.value = UpdateState.Idle
            } catch (e: Exception) {
                runCatching { installer.abandonSession(sessionId) }
                if (e is CancellationException) throw e
                _state.value = UpdateState.Failed(release)
            }
        }
    }

    /** Puts the offer away until the next fresh open, and stops a download under way. */
    fun dismiss() {
        download?.cancel()
        _state.value = UpdateState.Idle
    }

    /** Android could not install it (not one the person called off), so the dialog comes back to say so. */
    internal fun installFailed() {
        installing?.let { _state.value = UpdateState.Failed(it) }
    }

    private suspend fun write(release: AppRelease, session: PackageInstaller.Session) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(release.apkUrl).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: ${response.code}" }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.size.takeIf { it > 0 }
            session.openWrite("base.apk", 0, total ?: -1).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var written = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        _state.value = UpdateState.Downloading(release, total?.let { (written.toFloat() / it).coerceIn(0f, 1f) })
                    }
                    session.fsync(out)
                }
            }
        }
    }

    private fun statusReceiver(sessionId: Int): android.content.IntentSender {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
        // Mutable: the installer fills in the status and, when it needs the person, the intent that asks them.
        return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender
    }

    private fun latestRelease(): AppRelease? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        val release = json.decodeFromString<GitHubRelease>(body)
        val apk = release.assets.firstOrNull { it.name.startsWith("yuuka-") && it.name.endsWith(".apk") } ?: return null
        return AppRelease(version = release.tagName.removePrefix("v"), apkUrl = apk.downloadUrl, size = apk.size)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
    )

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/gavenda/yuuka/releases/latest"
        val json = Json { ignoreUnknownKeys = true }
    }
}
