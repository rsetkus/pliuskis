package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3PublishBuilder
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3UnsubscribeBuilder

private const val NOT_USED = "not used by the connect tests of this fake"

/** Fake CONNACK that carries only a return code. */
class FakeMqtt3ConnAck(private val returnCode: Mqtt3ConnAckReturnCode) : Mqtt3ConnAck {
    override fun getReturnCode(): Mqtt3ConnAckReturnCode = returnCode

    override fun isSessionPresent(): Boolean = false
}

/**
 * Fake HiveMQ client for the connect path of [HiveMqttClientManager]: it reports [state], and a blocking connect
 * answers with a CONNACK carrying [connAckReturnCode]. [connectCalls] counts the blocking connects. The client is its
 * own blocking view (`toBlocking()` returns `this`). Everything else fails loudly.
 */
class FakeMqtt3BlockingClient(
    private val state: MqttClientState,
    private val connAckReturnCode: Mqtt3ConnAckReturnCode,
) : Mqtt3BlockingClient {
    var connectCalls = 0
        private set

    override fun getState(): MqttClientState = state

    override fun connect(): Mqtt3ConnAck {
        connectCalls += 1
        return FakeMqtt3ConnAck(connAckReturnCode)
    }

    override fun connect(connect: Mqtt3Connect): Mqtt3ConnAck = error(NOT_USED)

    override fun connectWith(): Mqtt3ConnectBuilder.Send<Mqtt3ConnAck> = error(NOT_USED)

    override fun subscribe(subscribe: Mqtt3Subscribe): Mqtt3SubAck = error(NOT_USED)

    override fun subscribeWith(): Mqtt3SubscribeBuilder.Send.Start<Mqtt3SubAck> = error(NOT_USED)

    override fun publishes(filter: MqttGlobalPublishFilter): Mqtt3BlockingClient.Mqtt3Publishes = error(NOT_USED)

    override fun publishes(
        filter: MqttGlobalPublishFilter,
        manualAcknowledgement: Boolean,
    ): Mqtt3BlockingClient.Mqtt3Publishes = error(NOT_USED)

    override fun unsubscribe(unsubscribe: Mqtt3Unsubscribe) = error(NOT_USED)

    override fun unsubscribeWith(): Mqtt3UnsubscribeBuilder.SendVoid.Start = error(NOT_USED)

    override fun publish(publish: Mqtt3Publish) = error(NOT_USED)

    override fun publishWith(): Mqtt3PublishBuilder.SendVoid = error(NOT_USED)

    override fun disconnect() = error(NOT_USED)

    override fun getConfig(): Mqtt3ClientConfig = error(NOT_USED)

    override fun toRx(): Mqtt3RxClient = error(NOT_USED)

    override fun toAsync(): Mqtt3AsyncClient = error(NOT_USED)
}
