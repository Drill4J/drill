package com.epam.drill.core.methodbind

import com.epam.drill.core.di
import com.epam.drill.core.request.RetrieveDrillSessionFromRequest
import jvmapi.JNIEnvVar
import jvmapi.SetThreadLocalStorage
import jvmapi.jint
import jvmapi.jobject
import kotlinx.cinterop.*

const val SocketDispatcher = "Lsun/nio/ch/SocketDispatcher;"

fun read0(env: CPointer<JNIEnvVar>, obj: jobject, fd: jobject, address: DirectBufferAddress, len: jint): Int {
    initRuntimeIfNeeded()
    val retVal = di.originalMethod[::read0](env, obj, fd, address, len)
    if (retVal > 0)
        try {
            val request = address.rawString(retVal)
            if (request.startsWith("OPTIONS ") ||
                request.startsWith("GET ") ||
                request.startsWith("HEAD ") ||
                request.startsWith("POST ") ||
                request.startsWith("PUT ") ||
                request.startsWith("PATCH ") ||
                request.startsWith("DELETE ") ||
                request.startsWith("TRACE ") ||
                request.startsWith("CONNECT ")
            ) {
                SetThreadLocalStorage(
                    com.epam.drill.api.currentThread(),
                    StableRef.create(RetrieveDrillSessionFromRequest(request)).asCPointer()
                )
            }

        } catch (ex: Throwable) {
            println(ex.message)
        }
    return retVal
}

fun write0(env: CPointer<JNIEnvVar>, obj: jobject, fd: jobject, address: DirectBufferAddress, len: jint): jint {
    initRuntimeIfNeeded()
    var fakeLength = len
    var fakeBuffer = address
    var contentBodyBytes = address.rawString(len)
    println("\n\n\n_____________________________________________________________")
    println(contentBodyBytes.lines().joinToString("\n"))
//    if (contentBodyBytes.contains("Content-Type: text/html;charset=UTF-8")) {
//
//        val drillRequest = drillRequest()
//
//        contentBodyBytes =  contentBodyBytes.replace("HTTP/1.1 200","HTTP/1.1 200\nDrillServerAddress: ${drillRequest?.host + drillRequest?.drillSessionId}")
//        contentBodyBytes =  contentBodyBytes.replace("162b","16ec")
//
//        val map = contentBodyBytes.lines().map {
//            //            if (it.startsWith("HTTP/1.1 200")) {
////                it + "\n" + "DrillServerAddress: ${drillRequest?.host + drillRequest?.drillSessionId}"
////            } else {
//            it
////            }
//        }
//
////        val toUtf8 = map.joinToString(separator = "\n").toUtf8()
//        val toUtf8 = contentBodyBytes.toUtf8()
//        try {
//    contentBodyBytes.replace("chunked","huyanddasda")

            val alloc = nativeHeap.allocArray<ByteVar>(contentBodyBytes.toUtf8().size) { index -> value = contentBodyBytes.toUtf8()[index] }
            fakeBuffer = alloc.toLong()
//            fakeLength = toUtf8.size
//            println("$len _$fakeLength")
//        } catch (ex: Throwable) {
//            ex.printStackTrace()
//        }
//    }

    val retVal = di.originalMethod[::write0](env, obj, fd, fakeBuffer, len)
    return len
}
