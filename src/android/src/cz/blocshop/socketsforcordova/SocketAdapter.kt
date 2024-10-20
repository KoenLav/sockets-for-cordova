package cz.blocshop.socketsforcordova

import org.apache.cordova.CallbackContext
import java.io.IOException
import java.net.SocketException

interface SocketAdapter {
    fun open(host: String, port: Int, timeout: Int)
    fun write(data: ByteArray, callbackContext: CallbackContext)
    @Throws(IOException::class)
    fun shutdownWrite()
    @Throws(IOException::class)
    fun close()
    @Throws(SocketException::class)
    fun setOptions(options: SocketAdapterOptions)
    fun setOpenEventHandler(openEventHandler: Consumer<Void>)
    fun setOpenErrorEventHandler(openErrorEventHandler: Consumer<String>)
    fun setDataConsumer(dataConsumer: Consumer<ByteArray>)
    fun setCloseEventHandler(closeEventHandler: Consumer<Boolean>)
    fun setErrorEventHandler(errorEventHandler: Consumer<String>)
}
