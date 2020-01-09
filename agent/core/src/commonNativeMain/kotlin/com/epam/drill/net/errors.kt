package com.epam.drill.net

import kotlinx.cinterop.*
import kotlinx.io.core.ExperimentalIoApi
import platform.posix.*

@SharedImmutable
private val KnownPosixErrors = mapOf(
    EBADF to "EBADF",
    EWOULDBLOCK to "EWOULDBLOCK",
    EAGAIN to "EAGAIN",
    EBADMSG to "EBADMSG",
    EINTR to "EINTR",
    EINVAL to "EINVAL",
    EIO to "EIO",
    ECONNREFUSED to "ECONNREFUSED",
    ECONNABORTED to "ECONNABORTED",
    ECONNRESET to "ECONNRESET",
    ENOTCONN to "ENOTCONN",
    ETIMEDOUT to "ETIMEDOUT",
    EOVERFLOW to "EOVERFLOW",
    ENOMEM to "ENOMEM",
    ENOTSOCK to "ENOTSOCK",
    EADDRINUSE to "EADDRINUSE",
    ENOENT to "ENOENT"
)

@ExperimentalIoApi
sealed class PosixException(val errorCode: Int, message: String) : Exception(message) {
    @ExperimentalIoApi
    class BadFileDescriptorException(message: String) : PosixException(EBADF, message)

    @ExperimentalIoApi
    class TryAgainException(errno: Int = EAGAIN, message: String) : PosixException(errno, message)

    @ExperimentalIoApi
    class BadMessageException(message: String) : PosixException(EBADMSG, message)

    @ExperimentalIoApi
    class InterruptedException(message: String) : PosixException(EINTR, message)

    @ExperimentalIoApi
    class InvalidArgumentException(message: String) : PosixException(EINVAL, message)

    @ExperimentalIoApi
    class ConnectionResetException(message: String) : PosixException(ECONNRESET, message)

    @ExperimentalIoApi
    class ConnectionRefusedException(message: String) : PosixException(ECONNREFUSED, message)

    @ExperimentalIoApi
    class ConnectionAbortedException(message: String) : PosixException(ECONNABORTED, message)

    @ExperimentalIoApi
    class NotConnectedException(message: String) : PosixException(ENOTCONN, message)

    @ExperimentalIoApi
    class TimeoutIOException(message: String) : PosixException(ETIMEDOUT, message)

    @ExperimentalIoApi
    class NotSocketException(message: String) : PosixException(ENOTSOCK, message)

    @ExperimentalIoApi
    class AddressAlreadyInUseException(message: String) : PosixException(EADDRINUSE, message)

    @ExperimentalIoApi
    class NoSuchFileException(message: String) : PosixException(ENOENT, message)

    @ExperimentalIoApi
    class OverflowException(message: String) : PosixException(EOVERFLOW, message)

    @ExperimentalIoApi
    class NoMemoryException(message: String) : PosixException(ENOMEM, message)

    @ExperimentalIoApi
    class PosixErrnoException(errorCode: Int, message: String) : PosixException(errorCode, "$message ($errorCode)")

    companion object {
        @ExperimentalIoApi
        fun forErrno(errorCode: Int = errno, posixFunctionName: String? = null): PosixException = memScoped {
            println("raw errorCode: $errorCode")
            val posixConstantName = KnownPosixErrors[errorCode]
            val posixErrorCodeMessage = when {
                posixConstantName == null -> "POSIX error $errorCode"
                else -> "$posixConstantName ($errorCode)"
            }

            val message = when {
                posixFunctionName.isNullOrBlank() -> posixErrorCodeMessage + ": " + strerror(errorCode)
                else -> "$posixFunctionName failed, $posixErrorCodeMessage: ${strerror(errorCode)}"
            }

            when (errorCode) {
                EBADF -> BadFileDescriptorException(message)

                // it is not guaranteed that these errors have identical numeric values
                // so we need to specify both
                @Suppress("DUPLICATE_LABEL_IN_WHEN")
                EWOULDBLOCK, EAGAIN -> TryAgainException(
                    errorCode,
                    message
                )

                EBADMSG -> BadMessageException(message)
                EINTR -> InterruptedException(message)
                EINVAL -> InvalidArgumentException(message)
                ECONNREFUSED -> ConnectionRefusedException(message)
                ECONNABORTED -> ConnectionAbortedException(message)
                ECONNRESET -> ConnectionResetException(message)
                ENOTCONN -> NotConnectedException(message)
                ETIMEDOUT -> TimeoutIOException(message)
                EOVERFLOW -> OverflowException(message)
                ENOMEM -> NoMemoryException(message)
                ENOTSOCK -> NotSocketException(message)
                EADDRINUSE -> AddressAlreadyInUseException(message)
                ENOENT -> NoSuchFileException(message)
                else -> PosixErrnoException(errorCode, message)
            }
        }
    }
}

@Suppress("unused")
@Deprecated("Use errorCode instead.", ReplaceWith("errorCode"), level = DeprecationLevel.ERROR)
internal inline val PosixException.errno: Int
    get() = errorCode

private tailrec fun MemScope.strerror(errno: Int, size: size_t = 8192.convert()): String {
    val message = allocArray<ByteVar>(size.toLong())
    val result = strerror_r(errno, message, size)
    if (result == ERANGE) {
        return strerror(errno, size * 2.convert())
    }
    if (result != 0) {
        return "Unknown error ($errno)"
    }
    return message.toKString()
}
