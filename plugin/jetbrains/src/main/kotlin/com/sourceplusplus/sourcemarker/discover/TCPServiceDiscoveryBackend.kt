package com.sourceplusplus.sourcemarker.discover

import com.sourceplusplus.protocol.SourceMarkerServices
import com.sourceplusplus.protocol.SourceMarkerServices.Utilize
import com.sourceplusplus.protocol.status.MarkerConnection
import com.sourceplusplus.sourcemarker.SourceMarkerPlugin
import com.sourceplusplus.sourcemarker.settings.SourceMarkerConfig
import com.sourceplusplus.sourcemarker.settings.isSsl
import eu.geekplace.javapinning.JavaPinning
import eu.geekplace.javapinning.pin.Pin
import io.vertx.core.*
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import io.vertx.core.net.TrustOptions
import io.vertx.ext.bridge.BridgeEventType
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.servicediscovery.Record
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.*

/**
 * todo: description.
 *
 * @since 0.2.1
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
@Suppress("MagicNumber")
class TCPServiceDiscoveryBackend : ServiceDiscoveryBackend {

    companion object {
        private val log = LoggerFactory.getLogger(TCPServiceDiscoveryBackend::class.java)
        var socket: NetSocket? = null
    }

    private lateinit var vertx: Vertx
    private lateinit var client: NetClient
    private val setupPromise = Promise.promise<Void>()
    private val setupFuture = setupPromise.future()

    override fun init(vertx: Vertx, config: JsonObject) {
        this.vertx = vertx

        val hardcodedConfig = config.getJsonObject("hardcoded_config")
        val serviceDiscoveryEnabled = hardcodedConfig.getJsonObject("visible_settings").getBoolean("service_discovery")
        val pluginConfig = Json.decodeValue(
            config.getJsonObject("sourcemarker_plugin_config").toString(), SourceMarkerConfig::class.java
        )
        if (!serviceDiscoveryEnabled || pluginConfig.serviceHost.isNullOrBlank()) {
            log.warn("Service discovery disabled")
            return
        }

        var serviceHost = pluginConfig.serviceHost!!.substringAfter("https://").substringAfter("http://")
        val servicePort = hardcodedConfig.getInteger("tcp_service_port")
        if (serviceHost.contains(":")) {
            serviceHost = serviceHost.split(":")[0]
        }

        val certificatePins = mutableListOf<String>()
        certificatePins.addAll(pluginConfig.certificatePins)
        val hardcodedPin = hardcodedConfig.getString("certificate_pin")
        if (!hardcodedPin.isNullOrBlank()) {
            certificatePins.add(hardcodedPin)
        }

        GlobalScope.launch(vertx.dispatcher()) {
            try {
                client = if (certificatePins.isNotEmpty()) {
                    val options = NetClientOptions()
                        .setReconnectAttempts(Int.MAX_VALUE).setReconnectInterval(5000)
                        .setSsl(pluginConfig.isSsl())
                        .setTrustOptions(
                            TrustOptions.wrap(
                                JavaPinning.trustManagerForPins(certificatePins.map { Pin.fromString("CERTSHA256:$it") })
                            )
                        )
                    vertx.createNetClient(options)
                } else {
                    val options = NetClientOptions()
                        .setReconnectAttempts(Int.MAX_VALUE).setReconnectInterval(5000)
                        .setSsl(pluginConfig.isSsl())
                    vertx.createNetClient(options)
                }
                socket = client.connect(servicePort, serviceHost).await()
            } catch (throwable: Throwable) {
                log.warn("Failed to connect to service discovery server")
                setupPromise.fail(throwable)
                return@launch
            }
            socket!!.handler(FrameParser(TCPServiceFrameParser(vertx)))

            vertx.executeBlocking<Any> {
                var hardwareId: String? = null
                try {
                    //optional mac-address identification
                    val sb = StringBuilder()
                    val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                    while (networkInterfaces.hasMoreElements()) {
                        val ni = networkInterfaces.nextElement()
                        val hardwareAddress = ni.hardwareAddress
                        if (hardwareAddress != null) {
                            val hexadecimalFormat = mutableListOf<String>()
                            for (i in hardwareAddress.indices) {
                                hexadecimalFormat.add(String.format(Locale.ENGLISH, "%02X", hardwareAddress[i]))
                            }
                            sb.append(java.lang.String.join("-", hexadecimalFormat))
                        }
                    }
                    hardwareId = md5(sb.toString())
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                setupHandler(vertx, "get-records")
                setupHandler(vertx, Utilize.LIVE_VIEW)
                setupHandler(vertx, Utilize.LIVE_INSTRUMENT)
                setupHandler(vertx, Utilize.LOCAL_TRACING)
                setupHandler(vertx, Utilize.LOG_COUNT_INDICATOR)

                //setup connection
                val replyAddress = UUID.randomUUID().toString()
                val pc = MarkerConnection(SourceMarkerPlugin.INSTANCE_ID, System.currentTimeMillis(), hardwareId)
                val consumer: MessageConsumer<Boolean> = vertx.eventBus().localConsumer("local.$replyAddress")

                val promise = Promise.promise<Void>()
                consumer.handler {
                    //todo: handle false
                    if (it.body() == true) {
                        promise.complete()
                        consumer.unregister()
                        setupPromise.complete()
                    }
                }
                val headers = JsonObject()
                headers.put("token", pluginConfig.serviceToken!!)
                FrameHelper.sendFrame(
                    BridgeEventType.SEND.name.toLowerCase(),
                    SourceMarkerServices.Status.MARKER_CONNECTED,
                    replyAddress, headers, true, JsonObject.mapFrom(pc), socket!!
                )
            }
        }
    }

    private fun setupHandler(vertx: Vertx, address: String) {
        vertx.eventBus().localConsumer<JsonObject>(address) { resp ->
            val replyAddress = UUID.randomUUID().toString()
            val tempConsumer = vertx.eventBus().localConsumer<Any>("local.$replyAddress")
            tempConsumer.handler {
                resp.reply(it.body())
                tempConsumer.unregister()
            }

            val headers = JsonObject()
            resp.headers().entries().forEach { headers.put(it.key, it.value) }
            FrameHelper.sendFrame(
                BridgeEventType.SEND.name.toLowerCase(),
                address, replyAddress, headers, true, resp.body(), socket!!
            )
        }
    }

    override fun store(record: Record, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun remove(record: Record, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun remove(uuid: String, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun update(record: Record, resultHandler: Handler<AsyncResult<Void>>) {
        TODO("Not yet implemented")
    }

    override fun getRecords(resultHandler: Handler<AsyncResult<MutableList<Record>>>) {
        if (setupFuture.isComplete) {
            if (setupFuture.succeeded()) {
                vertx.eventBus().request<JsonObject>("get-records", null) {
                    resultHandler.handle(Future.succeededFuture(mutableListOf(Record(it.result().body()))))
                }
            } else {
                resultHandler.handle(Future.failedFuture(setupFuture.cause()))
            }
        } else {
            setupFuture.onComplete {
                if (it.succeeded()) {
                    vertx.eventBus().request<JsonArray>("get-records", null) {
                        val records = mutableListOf<Record>()
                        it.result().body().forEach { record ->
                            records.add(Record(record as JsonObject))
                        }
                        resultHandler.handle(Future.succeededFuture(records))
                    }
                } else {
                    resultHandler.handle(Future.failedFuture(it.cause()))
                }
            }
        }
    }

    override fun getRecord(uuid: String, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun name() = "tcp-service-discovery"

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }
}
