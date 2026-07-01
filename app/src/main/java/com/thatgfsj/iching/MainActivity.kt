package com.thatgfsj.iching

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thatgfsj.iching.ui.oracle.IChingOracleScreen
import com.thatgfsj.iching.ui.theme.IChingOracleTheme

/**
 * Single-activity host for the I Ching oracle. The app is just
 * one screen, so there is no Navigation Compose involved — the
 * Activity calls `setContent { ... IChingOracleScreen() }`
 * directly. If we ever add screens (history, browse all 64),
 * this is the place to wire in `NavHost`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IChingOracleTheme {
                IChingOracleScreen()
            }
        }
    }
}