package lt.setkus.pliuskis.shared

import lt.setkus.pliuskis.data.clientid.ClientIdProvider
import lt.setkus.pliuskis.data.clientid.UserDefaultsClientIdStore

/**
 * This app install's MQTT client ID (Swift: `InstallClientIdKt.installClientId()`): 23 characters of `0-9a-zA-Z`,
 * generated on first use and kept in the standard UserDefaults, so every launch gets the same one. The app puts it in
 * the `MqttSettings` it passes to [initShared]; it is also the `applicationId` of the device status topic.
 */
fun installClientId(): String = ClientIdProvider(UserDefaultsClientIdStore()).clientId()
