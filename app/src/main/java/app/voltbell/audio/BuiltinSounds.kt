package app.voltbell.audio

import android.content.Context
import android.net.Uri
import app.voltbell.R

object BuiltinSounds {
    data class Sound(val id: String, val title: String, val group: String, val res: Int)

    const val PREFIX = "builtin:"

    val all = listOf(
        Sound("loud_klaxon", "Klaxon", "Loud", R.raw.loud_klaxon),
        Sound("loud_bell", "Bell", "Loud", R.raw.loud_bell),
        Sound("loud_horn", "Horn", "Loud", R.raw.loud_horn),
        Sound("mellow_chime", "Chime", "Mellow", R.raw.mellow_chime),
        Sound("mellow_bowl", "Singing bowl", "Mellow", R.raw.mellow_bowl),
        Sound("mellow_keys", "Soft keys", "Mellow", R.raw.mellow_keys),
        Sound("water_brook", "Brook", "Water", R.raw.water_brook),
        Sound("water_waves", "Waves", "Water", R.raw.water_waves),
        Sound("water_rain", "Rain", "Water", R.raw.water_rain),
        Sound("nature_birds", "Birds", "Nature", R.raw.nature_birds),
        Sound("nature_rooster", "Rooster", "Nature", R.raw.nature_rooster),
        Sound("nature_crickets", "Crickets", "Nature", R.raw.nature_crickets),
    )

    fun stored(id: String) = "$PREFIX$id"

    fun fromStored(value: String): Sound? {
        if (!value.startsWith(PREFIX)) return null
        val id = value.removePrefix(PREFIX)
        return all.firstOrNull { it.id == id }
    }

    fun androidUri(context: Context, sound: Sound): Uri =
        Uri.parse("android.resource://${context.packageName}/${sound.res}")
}
