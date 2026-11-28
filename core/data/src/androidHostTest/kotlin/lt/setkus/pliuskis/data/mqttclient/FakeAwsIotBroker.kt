package lt.setkus.pliuskis.data.mqttclient

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.mqtt.MqttConnAckMessage
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader
import io.netty.handler.codec.mqtt.MqttConnectMessage
import io.netty.handler.codec.mqtt.MqttConnectReturnCode
import io.netty.handler.codec.mqtt.MqttDecoder
import io.netty.handler.codec.mqtt.MqttEncoder
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPubAckMessage
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttSubAckMessage
import io.netty.handler.codec.mqtt.MqttSubAckPayload
import io.netty.handler.codec.mqtt.MqttSubscribeMessage
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage
import io.netty.util.AttributeKey
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val WEBSOCKET_PATH = "/mqtt"
private const val MQTT_SUBPROTOCOL = "mqtt"
private const val MAX_HTTP_CONTENT = 65_536
private val SUBSCRIPTIONS = AttributeKey.valueOf<MutableSet<String>>("subscriptions")

/**
 * A fake AWS IoT for Android host tests: MQTT 3.1.1 over WebSocket at `ws://localhost:<port>/mqtt?<query>`, in the
 * test JVM (Netty), so no broker has to run outside the test. It accepts any query string (AWS IoT checks the SigV4
 * signature; this fake records it instead) and plays AWS IoT's answers:
 *
 * - [Mode.ACCEPT]: CONNACK 0, then SUBSCRIBE (SUBACK QoS 1), PUBLISH (PUBACK, recorded), PINGREQ, UNSUBSCRIBE.
 * - [Mode.HANG_UP_AFTER_CONNECT]: the WebSocket opens, and the CONNECT is answered by closing the connection without
 *   any CONNACK: what AWS IoT does for a signed-in identity without an AWS IoT policy (STORY-1 AC6).
 * - [Mode.REJECT_UPGRADE]: the WebSocket upgrade is answered with HTTP 403.
 *
 * [stop] closes the listener and every connection (a broker restart is [stop] then [start] on the same port); while it
 * is stopped a client gets a socket error (connection refused) before any answer.
 */
class FakeAwsIotBroker : AutoCloseable {

    enum class Mode { ACCEPT, HANG_UP_AFTER_CONNECT, REJECT_UPGRADE }

    /** A PUBLISH the client sent. */
    data class Published(val topic: String, val payload: String)

    @Volatile
    var mode: Mode = Mode.ACCEPT

    /** The request URI (`/mqtt?<query>`) of every WebSocket upgrade request, in order. */
    val handshakeUris: MutableList<String> = CopyOnWriteArrayList()

    /** The client ID of every CONNECT packet, in order. */
    val connects: MutableList<String> = CopyOnWriteArrayList()

    /** Every topic filter the clients subscribed to, in order, repeats included. */
    val subscriptions: MutableList<String> = CopyOnWriteArrayList()

    val published: MutableList<Published> = CopyOnWriteArrayList()

    var port: Int = 0
        private set

    private val boss = NioEventLoopGroup(1)
    private val workers = NioEventLoopGroup(2)
    private val clients = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    private val connected = ConcurrentHashMap.newKeySet<Channel>()
    private var server: Channel? = null

    /** Listens on [port] (a free port on the first start, the same one after a [stop]). */
    fun start(): FakeAwsIotBroker {
        val channel = ServerBootstrap()
            .group(boss, workers)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childHandler(Initializer())
            .bind(InetSocketAddress("127.0.0.1", port))
            .sync()
            .channel()
        port = (channel.localAddress() as InetSocketAddress).port
        server = channel
        return this
    }

    /** Stops listening and closes every connection, as a broker going down does. */
    fun stop() {
        server?.close()?.sync()
        server = null
        clients.close().awaitUninterruptibly()
        connected.clear()
    }

    /** Closes every client connection without a DISCONNECT (AWS IoT closing it, Wi-Fi lost), and keeps listening. */
    fun dropConnections() {
        clients.close().awaitUninterruptibly()
        connected.clear()
    }

    /** Sends a PUBLISH (QoS 0) of [payload] on [topic] to every connected client with a matching subscription. */
    fun publish(topic: String, payload: String) {
        connected
            .filter { client -> client.attr(SUBSCRIPTIONS).get().orEmpty().any { topicMatches(it, topic) } }
            .forEach { client ->
                val header = MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0)
                val message = MqttPublishMessage(
                    header,
                    MqttPublishVariableHeader(topic, 0),
                    Unpooled.copiedBuffer(payload.encodeToByteArray()),
                )
                client.writeAndFlush(message)
            }
    }

    /** How many clients are connected now (CONNECT accepted, connection still open). */
    val connectedClientCount: Int get() = connected.size

    /** Whether a connected client has subscribed to [filter] now. */
    fun hasSubscriber(filter: String): Boolean = connected.any { filter in it.attr(SUBSCRIPTIONS).get().orEmpty() }

    override fun close() {
        stop()
        boss.shutdownGracefully().awaitUninterruptibly()
        workers.shutdownGracefully().awaitUninterruptibly()
    }

    private inner class Initializer : ChannelInitializer<SocketChannel>() {
        override fun initChannel(channel: SocketChannel) {
            clients.add(channel)
            val webSocket = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(WEBSOCKET_PATH)
                .subprotocols(MQTT_SUBPROTOCOL)
                .checkStartsWith(true)
                .build()
            channel.pipeline().addLast(
                HttpServerCodec(),
                HttpObjectAggregator(MAX_HTTP_CONTENT),
                UpgradeGate(),
                WebSocketServerProtocolHandler(webSocket),
                WebSocketFrames(),
                MqttDecoder(),
                MqttEncoder.INSTANCE,
                MqttSession(),
            )
        }
    }

    /** Records each upgrade request's URI, and refuses it with HTTP 403 in [Mode.REJECT_UPGRADE]. */
    private inner class UpgradeGate : SimpleChannelInboundHandler<FullHttpRequest>(false) {
        override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
            handshakeUris += request.uri()
            if (mode == Mode.REJECT_UPGRADE) {
                request.release()
                val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
                ctx.writeAndFlush(response).addListener { ctx.close() }
            } else {
                ctx.fireChannelRead(request)
            }
        }
    }

    /** MQTT bytes in binary WebSocket frames, both ways. */
    private class WebSocketFrames : MessageToMessageCodec<BinaryWebSocketFrame, ByteBuf>() {
        override fun encode(ctx: ChannelHandlerContext, bytes: ByteBuf, out: MutableList<Any>) {
            out += BinaryWebSocketFrame(bytes.retain())
        }

        override fun decode(ctx: ChannelHandlerContext, frame: BinaryWebSocketFrame, out: MutableList<Any>) {
            out += frame.content().retain()
        }
    }

    /** One client's MQTT session. */
    private inner class MqttSession : SimpleChannelInboundHandler<MqttMessage>() {
        override fun channelRead0(ctx: ChannelHandlerContext, message: MqttMessage) {
            when (message.fixedHeader().messageType()) {
                MqttMessageType.CONNECT -> onConnect(ctx, message as MqttConnectMessage)
                MqttMessageType.SUBSCRIBE -> onSubscribe(ctx, message as MqttSubscribeMessage)
                MqttMessageType.UNSUBSCRIBE -> onUnsubscribe(ctx, message as MqttUnsubscribeMessage)
                MqttMessageType.PUBLISH -> onPublish(ctx, message as MqttPublishMessage)
                MqttMessageType.PINGREQ -> ctx.writeAndFlush(MqttMessage(fixedHeader(MqttMessageType.PINGRESP)))
                MqttMessageType.DISCONNECT -> ctx.close()
                else -> Unit
            }
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            connected.remove(ctx.channel())
            super.channelInactive(ctx)
        }

        private fun onConnect(ctx: ChannelHandlerContext, connect: MqttConnectMessage) {
            connects += connect.payload().clientIdentifier()
            if (mode == Mode.HANG_UP_AFTER_CONNECT) {
                ctx.close()
            } else {
                ctx.channel().attr(SUBSCRIPTIONS).set(ConcurrentHashMap.newKeySet())
                connected += ctx.channel()
                val accepted = MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false)
                ctx.writeAndFlush(MqttConnAckMessage(fixedHeader(MqttMessageType.CONNACK), accepted))
            }
        }

        private fun onSubscribe(ctx: ChannelHandlerContext, subscribe: MqttSubscribeMessage) {
            val filters = subscribe.payload().topicSubscriptions().map { it.topicName() }
            subscriptions += filters
            ctx.channel().attr(SUBSCRIPTIONS).get()?.addAll(filters)
            val granted = MqttSubAckPayload(filters.map { MqttQoS.AT_LEAST_ONCE.value() })
            val packetId = MqttMessageIdVariableHeader.from(subscribe.variableHeader().messageId())
            ctx.writeAndFlush(MqttSubAckMessage(fixedHeader(MqttMessageType.SUBACK), packetId, granted))
        }

        private fun onUnsubscribe(ctx: ChannelHandlerContext, unsubscribe: MqttUnsubscribeMessage) {
            ctx.channel().attr(SUBSCRIPTIONS).get()?.removeAll(unsubscribe.payload().topics().toSet())
            val packetId = MqttMessageIdVariableHeader.from(unsubscribe.variableHeader().messageId())
            ctx.writeAndFlush(MqttUnsubAckMessage(fixedHeader(MqttMessageType.UNSUBACK), packetId))
        }

        private fun onPublish(ctx: ChannelHandlerContext, publish: MqttPublishMessage) {
            val payload = publish.payload().toString(Charsets.UTF_8)
            published += Published(publish.variableHeader().topicName(), payload)
            if (publish.fixedHeader().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                val packetId = MqttMessageIdVariableHeader.from(publish.variableHeader().packetId())
                ctx.writeAndFlush(MqttPubAckMessage(fixedHeader(MqttMessageType.PUBACK), packetId))
            }
        }
    }
}

private fun fixedHeader(type: MqttMessageType) = MqttFixedHeader(type, false, MqttQoS.AT_MOST_ONCE, false, 0)

/** MQTT topic filter matching with `+` (one level) and `#` (the rest). */
private fun topicMatches(filter: String, topic: String): Boolean {
    val filterLevels = filter.split('/')
    val topicLevels = topic.split('/')
    val multiLevel = filterLevels.lastOrNull() == "#"
    val fixed = if (multiLevel) filterLevels.dropLast(1) else filterLevels
    val sizeMatches = if (multiLevel) topicLevels.size >= fixed.size else topicLevels.size == fixed.size
    return sizeMatches && fixed.indices.all { fixed[it] == "+" || fixed[it] == topicLevels[it] }
}
