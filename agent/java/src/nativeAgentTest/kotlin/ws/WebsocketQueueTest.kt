package ws

import Echo.startServer
import TestBase
import com.epam.drill.common.ws.URL
import com.epam.drill.net.close
import com.epam.drill.ws.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.minutes


class WebsocketQueueTest : TestBase() {

    @Ignore
    @Test
    fun shouldProcessBigMessage() = runTest(1.minutes) {
        val (serverFD, port) = startServer()
        val veryBigMessage = StringBuilder().apply { repeat(MESSAGE_SIZE) { append(".") } }.toString()
        val wsClient = RWebsocketClient("ws://localhost:$port")
        wsClient.onStringMessage.add { stringMessage ->
            assertEquals(veryBigMessage.length, stringMessage.length)
            (wsClient as? RawSocketWebSocketClient)?.client?.sendWsFrame(WsFrame(byteArrayOf(), WsOpcode.Close))
        }
        wsClient.onClose.add {
            wsClient.close()
            close(serverFD.toULong())
        }
        wsClient.send(veryBigMessage.apply {
            val mb: Float = this.encodeToByteArray().size / 1024f / 1024f
            println("$mb megabyte")
        })

    }

    @Test
    fun shouldProcessMultipleMessages() {
        val messageForSend = "any"
        runTest {
            val (serverFD, port) = startServer()
            var currentMessageIndex = 1
            val wsClient = RWebsocketClient(
                url = URL(
                    scheme = "ws",
                    userInfo = null,
                    host = "localhost",
                    path = "",
                    query = "",
                    fragment = null,
                    port = port
                ).fullUrl,
                protocols = listOf("x-kaazing-handshake"),
                origin = "",
                wskey = "",
                params = mutableMapOf()
            )
            wsClient.onOpen {
                println("Opened")
            }

            wsClient.onBinaryMessage.add { binary ->
                println(binary)
            }

            wsClient.onStringMessage.add { stringMessage ->
                assertEquals(messageForSend.length, stringMessage.length)
                if (currentMessageIndex++ == ITERATIONS) {
                    (wsClient as? RawSocketWebSocketClient)?.client?.sendWsFrame(
                        WsFrame(
                            byteArrayOf(),
                            WsOpcode.Close
                        )
                    )
                }
            }
            wsClient.onError.add {
                it.printStackTrace()
            }
            wsClient.onClose.add {
                wsClient.close()
                close(serverFD.toULong())
            }
            repeat(ITERATIONS) {
                wsClient.send(messageForSend)
            }

        }
    }

    companion object {
        private const val ITERATIONS = 100
        private const val MESSAGE_SIZE = 52309000
    }
}