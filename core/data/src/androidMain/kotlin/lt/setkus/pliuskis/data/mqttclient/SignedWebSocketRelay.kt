package lt.setkus.pliuskis.data.mqttclient

import co.touchlab.kermit.Logger
import com.hivemq.client.mqtt.MqttClientTransportConfig
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private const val TIMEOUT_MILLIS = 10_000
private const val MAX_REQUEST_HEAD_BYTES = 16 * 1024
private const val NONCE_BYTES = 16
private const val BUFFER_BYTES = 8 * 1024
private const val HTTPS_PORT = 443
private const val HTTP_PORT = 80
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val RELAY_PATH = "relay/"
private const val MQTT_SUBPROTOCOL = "mqtt"
private const val REQUEST_LINE_PARTS = 3
private val END_OF_HEAD = "\r\n\r\n".encodeToByteArray()

/**
 * The AWS IoT endpoint the release client connects to: `wss://<host>:<port>/mqtt` ([tls]) or, in tests, `ws://`.
 */
internal data class AwsIotWebSocketTarget(val host: String, val port: Int, val tls: Boolean) {
    companion object {
        /** The target of release [settings]: their host (the AWS IoT endpoint) and port, over TLS. */
        fun of(settings: MqttSettings): AwsIotWebSocketTarget =
            AwsIotWebSocketTarget(host = settings.host, port = settings.port, tls = true)
    }
}

/**
 * One connect attempt through the relay: HiveMQ connects to `ws://127.0.0.1:<port>/relay/<one-time nonce>` and the
 * relay opens the attempt's signed URL. [upstreamAnswered] says whether AWS IoT sent anything back (the upgrade's HTTP
 * answer, 101 or an error status), which tells a refusal from a network error.
 */
internal class RelayAttempt(private val localPort: Int, private val path: String) {
    @Volatile
    var upstreamAnswered = false
        internal set

    /** HiveMQ's transport for this attempt: plain WebSocket to the relay on this device; TLS is the relay's. */
    fun transportConfig(): MqttClientTransportConfig = MqttClientTransportConfig.builder()
        .serverHost(InetAddress.getLoopbackAddress())
        .serverPort(localPort)
        .webSocketConfig()
        .serverPath(path)
        .subprotocol(MQTT_SUBPROTOCOL)
        .applyWebSocketConfig()
        .socketConnectTimeout(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .mqttConnectTimeout(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .build()
}

/**
 * Lets HiveMQ open AWS IoT's SigV4-signed WebSocket URL exactly as signed. HiveMQ builds its WebSocket URL with
 * `java.net.URI`'s multi-argument constructor, which escapes every `%` again (`%2B` becomes `%252B`), so a signed
 * query can't be given to it: AWS IoT answers 403 to that, and to the session token's raw `+` too (checked against AWS
 * IoT on 2026-11-25). So HiveMQ connects over plain WebSocket to this relay, inside the app, and the relay forwards
 * the connection to [upstream] with the attempt's signed URL as the request target.
 *
 * - It listens on 127.0.0.1 only, on a free port, from the first [register] until [close].
 * - Each attempt gets a one-time random path ([register]); the relay forwards a connection only for a path it gave out
 *   and not yet used, so nothing else on the device can use it to reach AWS IoT with the user's URL.
 * - For a registered path, it replaces the request line's target with `/mqtt?<signed query>` and the `Host` header
 *   with the endpoint, opens TLS to the endpoint (the system's trusted CAs, and the host name checked), and then copies
 *   the bytes both ways until either side closes.
 *
 * It never logs a URL, a query or a path.
 */
internal class SignedWebSocketRelay(private val upstream: AwsIotWebSocketTarget) : Closeable {

    private class Registered(val signedUrl: String, val attempt: RelayAttempt)

    private val log = Logger.withTag("MqttRelay")
    private val random = SecureRandom()
    private val registered = ConcurrentHashMap<String, Registered>()

    @Volatile
    private var server: ServerSocket? = null

    /** Registers one attempt to [signedUrl] (`wss://<endpoint>/mqtt?<signed query>`) and gives HiveMQ's side of it. */
    fun register(signedUrl: String): RelayAttempt {
        val path = RELAY_PATH + nonce()
        val attempt = RelayAttempt(listeningPort(), path)
        registered["/$path"] = Registered(signedUrl, attempt)
        return attempt
    }

    override fun close() {
        synchronized(this) {
            server?.close()
            server = null
        }
        registered.clear()
    }

    @Synchronized
    private fun listeningPort(): Int {
        val socket = server ?: ServerSocket(0, 0, InetAddress.getLoopbackAddress()).also { opened ->
            server = opened
            Thread({ accept(opened) }, "pliuskis-mqtt-relay").apply { isDaemon = true }.start()
        }
        return socket.localPort
    }

    private fun accept(listener: ServerSocket) {
        runCatching {
            while (!listener.isClosed) {
                val client = listener.accept()
                Thread({ relay(client) }, "pliuskis-mqtt-relay-connection").apply { isDaemon = true }.start()
            }
        }
    }

    private fun relay(client: Socket) {
        client.use {
            try {
                client.soTimeout = TIMEOUT_MILLIS
                val head = readRequestHead(client.getInputStream())
                val entry = head?.let { registered.remove(it.target) }
                if (head != null && entry != null) forward(client, head, entry)
            } catch (error: IOException) {
                log.w { "MQTT relay connection to ${upstream.host}:${upstream.port} ended: ${error::class.simpleName}" }
            }
        }
    }

    private fun forward(client: Socket, head: RequestHead, entry: Registered) {
        openUpstream().use { remote ->
            remote.getOutputStream().apply {
                write(head.rewritten(entry.signedUrl, hostHeader()).encodeToByteArray())
                flush()
            }
            client.soTimeout = 0
            val upload = { copyThenClose(client.getInputStream(), remote.getOutputStream(), remote) }
            val toRemote = Thread(upload, "pliuskis-mqtt-relay-up").apply { isDaemon = true }
            toRemote.start()
            copy(remote.getInputStream(), client.getOutputStream()) { entry.attempt.upstreamAnswered = true }
            client.closeQuietly()
            toRemote.join(TIMEOUT_MILLIS.toLong())
        }
    }

    /** A TCP connection to [upstream], with TLS (host name checked) when it says so. */
    private fun openUpstream(): Socket {
        val plain = Socket().apply { connect(InetSocketAddress(upstream.host, upstream.port), TIMEOUT_MILLIS) }
        val socket = if (upstream.tls) secure(plain) else plain
        socket.soTimeout = 0
        return socket
    }

    private fun secure(plain: Socket): Socket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val tls = factory.createSocket(plain, upstream.host, upstream.port, true) as SSLSocket
        tls.soTimeout = TIMEOUT_MILLIS
        tls.startHandshake()
        val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify(upstream.host, tls.session)
        if (!verified) {
            tls.close()
            throw IOException("The TLS certificate does not match the AWS IoT endpoint")
        }
        return tls
    }

    private fun hostHeader(): String {
        val defaultPort = if (upstream.tls) HTTPS_PORT else HTTP_PORT
        return if (upstream.port == defaultPort) upstream.host else "${upstream.host}:${upstream.port}"
    }

    private fun nonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return bytes.joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
    }
}

/** The HTTP request head HiveMQ sent: its request line's target and its header lines. */
private class RequestHead(val method: String, val target: String, val version: String, val headers: List<String>) {
    /** The head with the target replaced by the signed URL's path and query, and `Host` by [host]. */
    fun rewritten(signedUrl: String, host: String): String {
        val signedTarget = "/" + signedUrl.substringAfter("://").substringAfter('/')
        val otherHeaders = headers.filterNot { it.substringBefore(':').trim().equals("Host", ignoreCase = true) }
        val lines = listOf("$method $signedTarget $version", "Host: $host") + otherHeaders
        return lines.joinToString("\r\n") + "\r\n\r\n"
    }
}

/** Reads the request head up to the empty line; null when it is too long, cut off or not a request line. */
private fun readRequestHead(input: InputStream): RequestHead? {
    val bytes = ByteArrayOutputStream()
    var ended = false
    while (!ended && bytes.size() < MAX_REQUEST_HEAD_BYTES) {
        val next = input.read()
        if (next < 0) break
        bytes.write(next)
        ended = bytes.size() >= END_OF_HEAD.size && bytes.toByteArray().endsWith(END_OF_HEAD)
    }
    val lines = if (ended) bytes.toString(Charsets.ISO_8859_1.name()).trimEnd().split("\r\n") else emptyList()
    val requestLine = lines.firstOrNull()?.split(' ')
    return requestLine?.takeIf { it.size == REQUEST_LINE_PARTS }?.let { (method, target, version) ->
        RequestHead(method, target, version, lines.drop(1))
    }
}

/** Copies [from] to [to] until either ends; [onFirstBytes] runs when the first bytes arrive. */
private fun copy(from: InputStream, to: OutputStream, onFirstBytes: () -> Unit = {}) {
    val buffer = ByteArray(BUFFER_BYTES)
    var read = readQuietly(from, buffer)
    if (read > 0) onFirstBytes()
    while (read > 0) {
        val written = runCatching {
            to.write(buffer, 0, read)
            to.flush()
        }.isSuccess
        read = if (written) readQuietly(from, buffer) else -1
    }
}

private fun copyThenClose(from: InputStream, to: OutputStream, closeWhenDone: Socket) {
    copy(from, to)
    closeWhenDone.closeQuietly()
}

private fun readQuietly(from: InputStream, buffer: ByteArray): Int = runCatching { from.read(buffer) }.getOrDefault(-1)

private fun ByteArray.endsWith(suffix: ByteArray): Boolean =
    size >= suffix.size && suffix.indices.all { this[size - suffix.size + it] == suffix[it] }

private fun Socket.closeQuietly() {
    runCatching { close() }
}
