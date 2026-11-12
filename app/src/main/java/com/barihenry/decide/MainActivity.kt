package com.barihenry.decide

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.barihenry.decide.model.DecisionStore
import com.barihenry.decide.ui.DecisionScreen
import com.barihenry.decide.ui.HomeScreen
import com.barihenry.decide.ui.OptionScreen
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = DecisionStore(File(filesDir, "decisions.json"))

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                // Minimal navigation: which decision / option is open (null = the level above).
                var decisionId by rememberSaveable { mutableStateOf<String?>(null) }
                var optionId by rememberSaveable { mutableStateOf<String?>(null) }

                val decision = decisionId?.let { store.decision(it) }
                val option = optionId?.let { id -> decision?.options?.find { it.id == id } }

                when {
                    decision != null && option != null -> {
                        BackHandler { optionId = null }
                        OptionScreen(store, decision, option, onBack = { optionId = null })
                    }
                    decision != null -> {
                        BackHandler { decisionId = null }
                        DecisionScreen(
                            store, decision,
                            onOpenOption = { optionId = it },
                            onBack = { decisionId = null },
                        )
                    }
                    else -> HomeScreen(store, onOpen = { decisionId = it; optionId = null })
                }
            }
        }
    }
}
