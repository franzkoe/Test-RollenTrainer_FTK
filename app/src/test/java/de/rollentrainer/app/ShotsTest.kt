package de.rollentrainer.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.rollentrainer.app.ble.BikeData
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.ble.FoundDevice
import de.rollentrainer.app.data.Sample
import de.rollentrainer.app.data.TYPE_HR
import de.rollentrainer.app.data.TYPE_TRAINER
import de.rollentrainer.app.data.Workout
import de.rollentrainer.app.data.summarize
import de.rollentrainer.app.ui.App
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** Rendert die Bildschirme in mehreren Fenstergrößen als PNG (nur zur Sichtkontrolle). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h1400dp-mdpi")
class ShotsTest {

    @get:Rule
    val rule = createComposeRule()

    private class Form(val name: String, val w: Int, val h: Int, val density: Float)

    private var form by mutableStateOf(Form("handy", 392, 850, 1.5f))

    private fun field(obj: Any, name: String): Any {
        val f = obj.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(obj)!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> set(obj: Any, name: String, value: T) {
        (field(obj, name) as MutableStateFlow<T>).value = value
    }

    private fun await(cond: () -> Boolean) {
        repeat(200) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(50)
        }
        error("Bedingung nicht erfüllt")
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onNodeWithTag("fenster").captureRoboImage("build/shots/${form.name}_$name.png")
    }

    private fun samples(): List<Sample> {
        val t0 = 1794500000000L
        var dist = 0.0
        return (1..2280).map { i ->
            val interval = (i > 600 && i < 1980 && ((i - 600) % 300) < 180)
            val p = (if (interval) 200 else if (i <= 600) 100 else 80) + (8 * sin(i / 7.0)).toInt()
            val v = 18.0 + p / 12.0
            dist += v / 3.6
            Sample(i, t0 + i * 1000L, p, v, dist, 85.0 + 4 * sin(i / 20.0), 110 + p / 5 + (3 * sin(i / 9.0)).toInt())
        }
    }

    @Test
    fun shots() {
        val engine = Engine.get(ApplicationProvider.getApplicationContext())
        rule.setContent {
            val f = form
            val conf = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = f.w
                screenHeightDp = f.h
                orientation = if (f.w > f.h) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(
                LocalConfiguration provides conf,
                LocalDensity provides Density(f.density),
            ) {
                Box(Modifier.requiredSize(f.w.dp, f.h.dp).testTag("fenster")) {
                    App(engine, {}, {}, {}, {}, {})
                }
            }
        }
        val ble = field(engine, "ble")
        val hr = field(engine, "hr")
        engine.toggleTheme()
        await { engine.lightTheme.value }

        engine.updateEditing(Workout(id = "w1", name = "5 × 3 min Schwelle"))
        engine.saveEditing()
        engine.addDevice(FoundDevice("C4:12:9A:55:01:7E", "D100 Wohnzimmer", -52), TYPE_TRAINER)
        await { engine.devices.value.size == 1 }
        engine.addDevice(FoundDevice("F1:0B:77:3C:21:90", "Polar H10", -60), TYPE_HR)
        await { engine.devices.value.size == 2 && engine.workouts.value.size == 1 }
        engine.updateEditing(Workout(id = "w2", name = "Grundlage 60", warmupMin = 5, intervalMin = 50, intervalW = 150, reps = 1, cooldownMin = 5))
        engine.saveEditing()
        await { engine.workouts.value.size == 2 }
        engine.updateEditing(Workout(id = "w3", name = "8 × 1 min VO2", intervalMin = 1, intervalW = 320, restMin = 2, reps = 8))
        engine.saveEditing()
        await { engine.workouts.value.size == 3 }
        set(ble, "_state", ConnState.READY)
        set(ble, "_address", "C4:12:9A:55:01:7E")
        set(ble, "_bike", BikeData(speedKmh = 31.4, cadenceRpm = 88.0, powerW = 196))
        set(hr, "_state", ConnState.READY)
        set(hr, "_address", "F1:0B:77:3C:21:90")
        set(hr, "_heartRate", 142)
        val list = samples()
        val summary = summarize("r1", "5 × 3 min Schwelle", list, 487)
        set(engine, "_rides", listOf(
            summary,
            summary.copy(id = "r2", name = "Freie Fahrt", startMs = summary.startMs - 86400000L * 2, durationSec = 1512, distanceM = 11240.0, avgPower = 141),
            summary.copy(id = "r3", name = "Grundlage 60", startMs = summary.startMs - 86400000L * 4, durationSec = 3600, distanceM = 29800.0, avgPower = 148),
        ))
        set(engine, "_live", LiveExtra(8420.0, 163))
        set(engine, "_ride", RideUi(countdown = null, elapsedSec = 1125, level = 7))
        val phases = Workout(id = "w1", name = "x").phases()
        set(engine, "_run", RunUi("5 × 3 min Schwelle", phases, null, 3, 95, 1145, 1135, 2280))
        set(engine, "_pending", RideDetail(summary, list))
        set(engine, "_detail", RideDetail(summary, list))
        set(engine, "_editing", Workout(id = "w1", name = "5 × 3 min Schwelle"))

        val forms = listOf(
            Form("1_handy_hoch", 392, 850, 1.5f),
            Form("2_handy_quer", 850, 392, 1.5f),
            Form("3_tablet_quer", 1280, 800, 1f),
            Form("4_tablet_hoch", 800, 1280, 1f),
        )
        for (f in forms) {
            form = f
            engine.dismissBatteryHint()
            set(engine, "_batteryHint", false)
            set(engine, "_screen", Screen.HOME); shot("01_home")
            set(engine, "_screen", Screen.DEVICES); shot("02_geraete")
            set(engine, "_screen", Screen.SETTINGS); shot("03_stufen")
            set(engine, "_screen", Screen.WORKOUTS); shot("04_trainings")
            set(engine, "_screen", Screen.EDITOR); shot("05_editor")
            set(engine, "_screen", Screen.FREE_RIDE); shot("06_freie_fahrt")
            set(engine, "_screen", Screen.WORKOUT_RUN); shot("07_training")
            set(engine, "_screen", Screen.SUMMARY); shot("08_zusammenfassung")
            set(engine, "_screen", Screen.HISTORY); shot("09_verlauf")
            set(engine, "_screen", Screen.RIDE_DETAIL); shot("10_fahrt")
        }

        // Andere Stufeneinstellung und dunkles Design
        engine.setLevelConfig(12, 800)
        await { engine.levelConfig.value.levels == 12 }
        engine.toggleTheme()
        await { !engine.lightTheme.value }
        set(engine, "_ride", RideUi(countdown = null, elapsedSec = 1125, level = 9))
        form = Form("5_tablet_quer_dunkel", 1280, 800, 1f)
        set(engine, "_screen", Screen.FREE_RIDE); shot("06_freie_fahrt_12x800")
        set(engine, "_screen", Screen.SETTINGS); shot("03_stufen_12x800")
        set(engine, "_screen", Screen.HOME); shot("01_home")
    }
}
