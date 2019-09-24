@file:Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")

package com.epam.drill.endpoints.agent

import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.system.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.reflect

private val logger = KotlinLogging.logger {}

class AgentHandler(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val pd: PluginDispatcher by kodein.instance()


    init {
        app.routing {
            agentWebsocket("/agent/attach") {
                val (agentConfig, needSync) = retrieveParams()
                val agentInfo = agentManager.agentConfiguration(agentConfig.id, agentConfig.buildVersion)
                agentInfo.ipAddress = call.request.local.remoteHost

                agentManager.put(agentInfo, this)
                agentManager.update()
                logger.info { "Agent WS is connected. Client's address is ${call.request.local.remoteHost}" }
                if (needSync)
                    agentManager.updateAgentConfig(agentInfo)


                val sslPort = app.securePort()

                sendToTopic("/agent/config", ServiceConfig(sslPort))

                createWsLoop(agentInfo)

            }
        }
    }

    private fun DefaultWebSocketServerSession.retrieveParams(): Pair<AgentConfig, Boolean> {
        val agentConfig = Cbor.loads(AgentConfig.serializer(), call.request.headers[AgentConfigParam]!!)
        val needSync = call.request.headers[NeedSyncParam]!!.toBoolean()
        return agentConfig to needSync
    }

    private suspend fun DefaultWebSocketServerSession.createWsLoop(agentInfo: AgentInfo) {
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = Message.serializer() parse frame.readText()
                    when (message.type) {
                        MessageType.PLUGIN_DATA -> {
                            logger.debug(message.data)
                            pd.processPluginData(message.data, agentInfo)
                        }
                        MessageType.MESSAGE -> {
                            logger.warn { "Message: '${message}' " }
                        }
                        MessageType.MESSAGE_DELIVERED -> {
                            subscribers[message.destination]?.apply {
                                if (callback.reflect()?.parameters?.get(0)?.type == Unit::class.starProjectedType)
                                    (callback as suspend (Unit) -> Unit).invoke(Unit)
                                else
                                    callback.invoke(message.data)
                                state = false
                            }
                        }
                        else -> {
                            logger.warn { "How do you want to process '${message.type}' event?" }
                        }
                    }

                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            logger.info { "agentDisconnected ${agentInfo.id} disconnected!" }
            agentManager.remove(agentInfo)
        }
    }
}