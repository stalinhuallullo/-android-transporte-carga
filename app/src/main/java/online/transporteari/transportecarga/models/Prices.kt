package online.transporteari.transportecarga.models
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class Prices (
    val km: Double? = null,
    val min: Double? = null,
    val minValue: Double? = null,
    val difference: Double? = null
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<Prices>(json)
    }
}