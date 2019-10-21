package com.epam.drill.client

import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.dataclasses.*
import com.epam.drill.endpoints.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*


class AdminUiChannels {
    val agentChannel = Channel<AgentInfoWebSocketSingle?>()
    val agentBuildsChannel = Channel<Set<AgentBuildVersionJson>?>()
    val buildsChannel = Channel<List<BuildSummaryWebSocket>?>()
    val agentsChannel = Channel<Set<AgentInfoWebSocket>?>()
    val allPluginsChannel = Channel<Set<PluginWebSocket>?>()
    val notificationsChannel = Channel<Set<Notification>?>()
    val agentPluginInfoChannel = Channel<Set<PluginWebSocket>?>()

    suspend fun getAgent() = agentChannel.receive()
    suspend fun getAgentBuilds() = agentBuildsChannel.receive()
    suspend fun getBuilds() = buildsChannel.receive()
    suspend fun getAllAgents() = agentsChannel.receive()
    suspend fun getAllPluginsInfo() = allPluginsChannel.receive()
    suspend fun getNotifications() = notificationsChannel.receive()
    suspend fun getAgentPluginInfo() = agentPluginInfoChannel.receive()

}

class UIEVENTLOOP(val cs: Map<String, AdminUiChannels>, val uiStreamDebug: Boolean) {


    @UseExperimental(ExperimentalCoroutinesApi::class)
    fun Application.queued(wsTopic: WsTopic, incoming: ReceiveChannel<Frame>) = this.launch {
        incoming.consumeEach {
            when (it) {
                is Frame.Text -> {
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    if (uiStreamDebug)
                        println("UI: $parseJson")
                    val messageType = WsMessageType.valueOf(parseJson[WsReceiveMessage::type.name]!!.content)
                    val url = parseJson[WsReceiveMessage::destination.name]!!.content
                    val content = parseJson[WsReceiveMessage::message.name]!!.toString()
                    val (_, type) = wsTopic.getParams(url)
                    val notEmptyResponse = content != "\"\""
                    when (messageType) {
                        WsMessageType.MESSAGE ->
                            this@queued.launch {
                                when (type) {
                                    is WsRoutes.GetAllAgents -> {
//                                if (notEmptyResponse) {
//                                    agentsChannel.send((AgentInfoWebSocket.serializer().set parse content))
//                                } else {
//                                    agentsChannel.send(null)
//                                }
                                    }
                                    is WsRoutes.GetAgent -> {

                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentChannel.send(AgentInfoWebSocketSingle.serializer() parse content)
                                        } else {
                                            cs[type.agentId]!!.agentChannel.send(null)
                                        }

                                    }
                                    is WsRoutes.GetAgentBuilds -> {
                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentBuildsChannel.send((AgentBuildVersionJson.serializer().set parse content))
                                        } else {
                                            cs[type.agentId]!!.agentBuildsChannel.send(null)
                                        }
                                    }
                                    is WsRoutes.GetAllPlugins -> {
                                        //                                if (notEmptyResponse) {
//                                    allPluginsChannel.send((PluginWebSocket.serializer().set parse content))
//                                } else {
//                                    allPluginsChannel.send(null)
//                                }
                                    }

                                    is WsRoutes.GetBuilds -> {
                                        if (notEmptyResponse) {
                                            cs.getValue(type.agentId)
                                                .buildsChannel.send((BuildSummaryWebSocket.serializer().list parse content))
                                        } else {
                                            cs.getValue(type.agentId).buildsChannel.send(null)
                                        }
                                    }

                                    is WsRoutes.GetNotifications -> {
                                        //                                if (notEmptyResponse) {
//                                    notificationsChannel.send(Notification.serializer().set parse content)
//                                } else {
//                                    notificationsChannel.send(null)
//                                }
                                    }

                                    is WsRoutes.GetPluginConfig -> {
                                    }
                                    is WsRoutes.GetPluginInfo -> {
                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentPluginInfoChannel.send(PluginWebSocket.serializer().set parse content)
                                        } else {
                                            cs[type.agentId]!!.agentPluginInfoChannel.send(null)
                                        }
                                    }
                                }
                            }
                        else -> {
                        }
                    }
                }
                is Frame.Close -> {}
                else -> throw RuntimeException(" read not FRAME.TEXT frame.")
            }
        }

    }
}


class Agent(
    val app: Application,
    val agentId: String,
    val incoming: ReceiveChannel<Frame>,
    val outgoing: SendChannel<Frame>,
    val agentStreamDebug: Boolean
) {
    private val serviceConfig = Channel<ServiceConfig?>()
    private val `set-packages-prefixes` = Channel<String>()
    private val `load-classes-data` = Channel<String>()
    private val plugins = Channel<PluginMetadata>()
    private val pluginBinary = Channel<ByteArray>()

    suspend fun getServiceConfig() = serviceConfig.receive()
    suspend fun getLoadedPlugin(block: suspend (PluginMetadata, ByteArray) -> Unit) {
        val receive = plugins.receive()
        val pluginBinarys = getPluginBinary()
        block(receive, pluginBinarys)
        outgoing.send(AgentMessage(MessageType.MESSAGE_DELIVERED, "/plugins/load", ""))

    }

    suspend fun getPluginBinary() = pluginBinary.receive()
    suspend fun `get-set-packages-prefixes`(): String {
        val receive = `set-packages-prefixes`.receive()
        outgoing.send(
            AgentMessage(
                MessageType.MESSAGE_DELIVERED,
                "/agent/set-packages-prefixes",
                ""
            )
        )
        return receive
    }

    suspend fun `get-load-classes-data`(): String {
        val receive = `load-classes-data`.receive()
        outgoing.send(
            AgentMessage(
                MessageType.MESSAGE_DELIVERED,
                "/agent/load-classes-data",
                ""
            )
        )
        outgoing.send(AgentMessage(MessageType.START_CLASSES_TRANSFER, "", ""))
        outgoing.send(AgentMessage(MessageType.FINISH_CLASSES_TRANSFER, "", ""))
        delay(500)
        return receive
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    fun queued() = app.launch {
        if (agentStreamDebug) {
            println()
            println("______________________________________________________________")
        }
        incoming.consumeEach {
            when (it) {
                is Frame.Text -> {
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    val url = parseJson[Message::destination.name]!!.content
                    val content = parseJson[Message::data.name]!!.content
                    if (agentStreamDebug)
                        println("AGENT $agentId: $parseJson")
                    app.launch {
                        when (url) {
                            "/agent/config" -> serviceConfig.send(ServiceConfig.serializer() parse content)
                            "/agent/set-packages-prefixes" -> `set-packages-prefixes`.send(content)
                            "/agent/load-classes-data" -> `load-classes-data`.send(content)
                            "/plugins/load" -> plugins.send(PluginMetadata.serializer() parse content)
                            "/plugins/resetPlugin" -> {
                            }
                            else -> TODO("$url is not implemented yet")
                        }
                    }
                }
                is Frame.Binary -> {
                    pluginBinary.send(it.readBytes())
                }
            }
        }
    }

}

