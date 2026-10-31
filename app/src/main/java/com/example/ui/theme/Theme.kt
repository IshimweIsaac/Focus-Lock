package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BgDark,
    onBackground = OnBgDark,
    surface = BgDark,
    onSurface = OnBgDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for requested aesthetic
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disable dynamic colors to strictly use our palette
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
