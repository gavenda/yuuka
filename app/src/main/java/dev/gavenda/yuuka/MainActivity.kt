package dev.gavenda.yuuka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.auth.AuthState
import dev.gavenda.yuuka.domain.ThemeMode
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.ui.YuukaApp
import dev.gavenda.yuuka.ui.auth.AuthScreen
import dev.gavenda.yuuka.ui.theme.YuukaTheme
import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {
    private val authManager: AuthManager by lazy { getKoin().get() }
    private val themePreference: ThemePreference by lazy { getKoin().get() }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by themePreference.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by themePreference.dynamicColor.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.system -> isSystemInDarkTheme()
                ThemeMode.light -> false
                ThemeMode.dark -> true
            }

            YuukaTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                val authState by authManager.authState.collectAsStateWithLifecycle()

                // The XML theme follows the system, but the in-app theme mode can override it, so
                // without a Surface nothing paints the Compose scheme's background or sets a content
                // colour, and a forced dark scheme ends up as pale text on a light window.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (authState) {
                            is AuthState.Loading -> {
                                val loadingLabel = stringResource(R.string.loading_ellipsis)
                                LoadingIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingLabel })
                            }

                            is AuthState.Unauthenticated -> AuthScreen(onLogin = { authManager.login(this@MainActivity) })

                            is AuthState.Authenticated -> YuukaApp(onSignOut = { authManager.logout(this@MainActivity) })
                        }
                    }
                }
            }
        }
    }
}
