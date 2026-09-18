package dev.gavenda.yuuka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                // The window background comes from the (always light) XML theme, so without a Surface
                // nothing paints the Compose scheme's background or sets a content colour, and a dark
                // scheme ends up as pale text on a light window.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (authState) {
                            is AuthState.Loading -> Text("Loading…", modifier = Modifier.align(Alignment.Center))

                            is AuthState.Unauthenticated -> AuthScreen(onLogin = { authManager.login(this@MainActivity) })

                            is AuthState.Authenticated -> YuukaApp(onSignOut = { authManager.logout(this@MainActivity) })
                        }
                    }
                }
            }
        }
    }
}
