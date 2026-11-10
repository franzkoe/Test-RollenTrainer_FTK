package de.rollentrainer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rollentrainer")

/** Dauerhafte Speicherung (DataStore): Geräte, Trainingsvorlagen, Einstellungen. */
class Store(private val context: Context) {

    private val keyDevices = stringPreferencesKey("devices")
    private val keyWorkouts = stringPreferencesKey("workouts")
    private val keyLastDevice = stringPreferencesKey("last_device")
    private val keyBatteryHint = booleanPreferencesKey("battery_hint_shown")
    private val keyLightTheme = booleanPreferencesKey("light_theme")

    val devices: Flow<List<SavedDevice>> =
        context.dataStore.data.map { parseDevices(it[keyDevices]) }

    val workouts: Flow<List<Workout>> =
        context.dataStore.data.map { parseWorkouts(it[keyWorkouts]) }

    val lastDevice: Flow<String?> = context.dataStore.data.map { it[keyLastDevice] }

    val batteryHintShown: Flow<Boolean> =
        context.dataStore.data.map { it[keyBatteryHint] ?: false }

    val lightTheme: Flow<Boolean> = context.dataStore.data.map { it[keyLightTheme] ?: false }

    suspend fun saveDevices(list: List<SavedDevice>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().put("address", d.address).put("name", d.name))
        }
        context.dataStore.edit { it[keyDevices] = arr.toString() }
    }

    suspend fun saveWorkouts(list: List<Workout>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("warmupMin", w.warmupMin)
                    .put("warmupW", w.warmupW)
                    .put("intervalMin", w.intervalMin)
                    .put("intervalW", w.intervalW)
                    .put("restMin", w.restMin)
                    .put("restW", w.restW)
                    .put("reps", w.reps)
                    .put("cooldownMin", w.cooldownMin)
                    .put("cooldownW", w.cooldownW)
            )
        }
        context.dataStore.edit { it[keyWorkouts] = arr.toString() }
    }

    suspend fun setLastDevice(address: String?) {
        context.dataStore.edit {
            if (address == null) it.remove(keyLastDevice) else it[keyLastDevice] = address
        }
    }

    suspend fun setBatteryHintShown() {
        context.dataStore.edit { it[keyBatteryHint] = true }
    }

    suspend fun setLightTheme(light: Boolean) {
        context.dataStore.edit { it[keyLightTheme] = light }
    }

    private fun parseDevices(json: String?): List<SavedDevice> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedDevice(o.getString("address"), o.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseWorkouts(json: String?): List<Workout> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Workout(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    warmupMin = o.optInt("warmupMin", 10),
                    warmupW = o.optInt("warmupW", 100),
                    intervalMin = o.optInt("intervalMin", 3),
                    intervalW = o.optInt("intervalW", 200),
                    restMin = o.optInt("restMin", 2),
                    restW = o.optInt("restW", 80),
                    reps = o.optInt("reps", 5),
                    cooldownMin = o.optInt("cooldownMin", 0),
                    cooldownW = o.optInt("cooldownW", 80),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
