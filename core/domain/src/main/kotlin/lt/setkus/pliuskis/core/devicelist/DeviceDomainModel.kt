package lt.setkus.pliuskis.core.devicelist

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class DeviceDomainModel(
    val name: String,
    val deviceId: String,
    val type: DeviceType,
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant
)

@Serializable(with = DeviceTypeSerializer::class)
enum class DeviceType {
    @SerialName("plant")
    PLANT,
    UNKNOWN;
    val serialName: String
        get() = when (this) {
            PLANT -> "plant"
            UNKNOWN -> "unknown"
        }
}

object DeviceTypeSerializer : KSerializer<DeviceType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DeviceType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DeviceType {
        val value = decoder.decodeString()
        return DeviceType.entries.find {
            it.serialName.equals(value, ignoreCase = true)
        } ?: DeviceType.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: DeviceType) {
        encoder.encodeString(value.serialName)
    }
}
