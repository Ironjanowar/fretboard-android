package dev.ironjanowar.fretboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.ironjanowar.fretboard.ui.FretboardApp

/**
 * The single activity of the application.
 *
 * It only hosts the Compose tree: every musical answer on screen comes from the
 * engine through the generated bindings, never from Kotlin.
 *
 * The session lives in [FretboardViewModel], which the activity's retained
 * `ViewModelStore` keeps across a configuration change. Rotation therefore
 * recreates this activity and its composition without recreating the session —
 * which is what the reported rotation bug was: the session used to be held by
 * the composition itself, so turning the phone threw it away.
 *
 * The manifest deliberately declares no `android:configChanges`: the activity
 * must still be recreated so the platform can re-resolve resources, and the fix
 * belongs in where the state lives, not in suppressing the recreation.
 */
class MainActivity : ComponentActivity() {

    private val fretboard: FretboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FretboardApp(fretboard) }
    }
}
