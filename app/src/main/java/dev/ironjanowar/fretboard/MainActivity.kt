package dev.ironjanowar.fretboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.ironjanowar.fretboard.links.DeliveredText
import dev.ironjanowar.fretboard.links.asDeliveredIntent
import dev.ironjanowar.fretboard.links.parseDeliveredIntent
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
 *
 * Since `A19` this activity is also an `ACTION_SEND` target: a link shared from
 * another app arrives here as a delivery. Reading one is all that happens here — the
 * parser decides whether the delivery is a text at all, the holder hands it to the
 * arbitration, and the engine reads the link. Nothing about a URL or a musical value
 * is decided in an activity.
 */
class MainActivity : ComponentActivity() {

    private val fretboard: FretboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FretboardApp(fretboard) }
        // A launch, and only a launch: on a configuration change the platform hands the
        // same intent to a recreated activity, and reading it again would take the
        // session the user is looking at away from them.
        if (savedInstanceState == null) deliver(intent)
    }

    /**
     * A delivery that arrived while this activity was already running.
     *
     * `setIntent` keeps the activity's own intent in step, so a recreation after this
     * (a rotation) still describes the delivery that is now on screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    /** Read one delivered intent, unless it delivered nothing at all. */
    private fun deliver(intent: Intent) {
        val delivered = parseDeliveredIntent(intent.asDeliveredIntent())
        if (delivered is DeliveredText.Nothing) return
        fretboard.deliver(delivered)
    }
}
