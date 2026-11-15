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
import de.rollentrainer.app.data.AiConfig
import de.rollentrainer.app.data.Kind
import de.rollentrainer.app.data.PRESET_PLANS
import de.rollentrainer.app.data.Profile
import de.rollentrainer.app.data.Sample
import de.rollentrainer.app.data.TYPE_TRAINER
import de.rollentrainer.app.data.Workout
import de.rollentrainer.app.data.buildSession
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

/** Rendert die Bildschirme des Trainingsplans als PNG (nur zur Sichtkontrolle). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h2800dp-mdpi")
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
            val p = 150 + (8 * sin(i / 7.0)).toInt()
            val v = 18.0 + p / 12.0
            dist += v / 3.6
            Sample(i, t0 + i * 1000L, p, v, dist, 85.0, 130)
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
        engine.dismissBatteryHint()
        set(engine, "_batteryHint", false)
        engine.updateEditing(Workout(id = "w1", name = "5 × 3 min Schwelle"))
        engine.saveEditing()
        engine.addDevice(FoundDevice("C4:12:9A:55:01:7E", "D100 Wohnzimmer", -52), TYPE_TRAINER)
        await { engine.devices.value.size == 1 && engine.workouts.value.size == 1 }
        set(ble, "_state", ConnState.READY)
        set(ble, "_address", "C4:12:9A:55:01:7E")
        set(ble, "_bike", BikeData(speedKmh = 31.4, cadenceRpm = 88.0, powerW = 196))
        set(hr, "_state", ConnState.READY)
        set(hr, "_heartRate", 142)
        set(engine, "_live", LiveExtra(8420.0, 163))
        val list = samples()
        val summary = summarize("r1", "Sweet Spot 3 × 12 min", list, 487)
        set(engine, "_rides", listOf(summary))

        val phone = Form("1_handy", 392, 850, 1.5f)
        val long = Form("1_handy_lang", 392, 2600, 1.5f)
        val wide = Form("2_handy_quer", 850, 392, 1.5f)
        val tabQ = Form("3_tablet_quer", 1280, 800, 1f)
        val tabH = Form("4_tablet_hoch", 800, 1280, 1f)
        val all = listOf(phone, long, wide, tabQ, tabH)

        // ---- Leerer Stand
        set(engine, "_rides", emptyList<de.rollentrainer.app.data.RideSummary>())
        for (f in listOf(phone, long)) {
            form = f
            set(engine, "_screen", Screen.HOME); shot("01_home_leer")
            set(engine, "_screen", Screen.DASHBOARD); shot("02_dashboard_leer")
        }

        // ---- Mit Daten: Fahrten über zehn Wochen, Erholungswerte, VO2max, Plan
        val today = engine.today()
        val rides = (0..70).filter { it % 2 == 0 }.map { d ->
            val up = (70 - d) / 4
            summary.copy(
                id = "r$d", startMs = System.currentTimeMillis() - d * 86_400_000L,
                avgPower = 150 + up, avgHr = 138, np = 158 + up, best20 = 190 + up,
                best5 = 230 + up, load = 45 + up, metrics = true,
            )
        }
        set(engine, "_rides", rides)
        val store = field(engine, "store") as de.rollentrainer.app.data.Store
        kotlinx.coroutines.runBlocking {
            store.setRecovery(
                (1..12).map { de.rollentrainer.app.data.Recovery(today - it, 50 + it % 3, 62 - it % 4, 430 + it * 3, 3 + it % 2) } +
                    de.rollentrainer.app.data.Recovery(today, 57, 49, 340, 2)
            )
            store.setVo2(
                listOf(
                    de.rollentrainer.app.data.Vo2(today, 51.5),
                    de.rollentrainer.app.data.Vo2(today - 30, 50.0),
                    de.rollentrainer.app.data.Vo2(today - 75, 48.5),
                )
            )
        }
        await { engine.recovery.value.size == 13 && engine.vo2.value.size == 3 }
        engine.setProfile(Profile(ftp = 220, byHr = false, weight = 75))
        await { engine.profile.value.power }
        engine.startPlan(PRESET_PLANS[2])
        await { engine.planState.value != null }
        engine.markNextDone(); await { engine.planState.value!!.done.size == 1 }
        set(engine, "_ride", RideUi(countdown = null, level = 6, elapsedSec = 1284))
        set(engine, "_pendingFree", true)
        set(engine, "_pending", RideDetail(summary.copy(name = "Freie Fahrt"), list))
        set(engine, "_detail", RideDetail(rides[0], list))
        for (f in all) {
            form = f
            set(engine, "_screen", Screen.HOME); shot("03_home")
            set(engine, "_screen", Screen.DASHBOARD); shot("04_dashboard")
            set(engine, "_screen", Screen.SETTINGS); shot("05_einstellungen")
            set(engine, "_screen", Screen.FREE_RIDE); shot("06_freie_fahrt")
            set(engine, "_screen", Screen.PLAN); shot("07_plan_erholung")
        }
        form = long
        set(engine, "_screen", Screen.RIDE_DETAIL); shot("08_fahrt")
        set(engine, "_screen", Screen.SUMMARY); shot("09_zusammenfassung")

        // ---- Hell
        engine.toggleTheme()
        await { engine.lightTheme.value }
        for (f in listOf(Form("5_handy_hell", 392, 850, 1.5f), Form("5_handy_hell_lang", 392, 2600, 1.5f))) {
            form = f
            set(engine, "_screen", Screen.HOME); shot("03_home")
            set(engine, "_screen", Screen.DASHBOARD); shot("04_dashboard")
            set(engine, "_screen", Screen.SETTINGS); shot("05_einstellungen")
        }
    }
}
