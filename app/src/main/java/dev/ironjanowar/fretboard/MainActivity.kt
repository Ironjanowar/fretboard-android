package dev.ironjanowar.fretboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ironjanowar.fretboard.ui.FretboardApp

/**
 * The single activity of the application.
 *
 * It only hosts the Compose tree: every musical answer on screen comes from the
 * engine through the generated bindings, never from Kotlin.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FretboardApp() }
    }
}
