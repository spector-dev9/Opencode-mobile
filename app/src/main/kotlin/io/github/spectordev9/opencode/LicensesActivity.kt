package io.github.spectordev9.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spectordev9.opencode.data.AppPreferences
import io.github.spectordev9.opencode.data.ThemeMode
import io.github.spectordev9.opencode.ui.theme.OpenCodeMobileTheme

class LicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val licenses = readLicenses()
        val screenTitle = getString(R.string.licenses_title)
        val preferences = AppPreferences(applicationContext)
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            OpenCodeMobileTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = licenses,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readLicenses(): String = buildString {
        append(readAsset("licenses/NOTICES.md"))
        append("\n\n")
        append(readAsset("licenses/SOURCE-OFFER.md"))
        val names = assets.list("licenses/texts").orEmpty()
            .filter { it.endsWith(".txt") || it.endsWith(".md") }
            .sorted()
        for (name in names) {
            append("\n\n")
            append(name)
            append("\n")
            append("=".repeat(name.length))
            append("\n\n")
            append(readAsset("licenses/texts/$name"))
        }
    }

    private fun readAsset(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
