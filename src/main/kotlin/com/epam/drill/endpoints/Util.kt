package com.epam.drill.endpoints

import com.epam.drill.common.*
import io.ktor.http.cio.websocket.*
import kotlinx.serialization.*

fun<T> KSerializer<T>.agentWsMessage(destination: String, message: T): Frame.Text {
    val toJson = WsMessage.serializer() stringify
        WsMessage(WsMessageType.MESSAGE, destination, if (message is String) message else this stringify message)

    println(toJson)
    return Frame.Text(toJson)
}