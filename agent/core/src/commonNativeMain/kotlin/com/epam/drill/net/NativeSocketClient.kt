package com.epam.drill.net

import kotlinx.cinterop.*
import platform.posix.*

class NativeSocketClient(sockfd: Int) : NativeSocket(sockfd) {
    companion object {
        operator fun invoke(): NativeSocketClient {
            val socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
            return NativeSocketClient(socket)
        }
    }

    private var _connected = false
    override fun isAlive() = _connected
    override fun setIsAlive(isAlive: Boolean) {
        _connected = false
    }

    @Suppress("RemoveRedundantCallsOfConversionMethods")
    fun connect(host: String, port: Int) {
        memScoped {
            val inetaddr = resolveAddress(host, port)
            checkErrors("getaddrinfo")

            @Suppress("RemoveRedundantCallsOfConversionMethods") val connected =
                connect(sockfd, inetaddr, sockaddr_in.size.convert())
            checkErrors("connect")
            setNonBlocking()
            if (connected != 0) {
                _connected = false
            }
            _connected = true
        }
    }
}