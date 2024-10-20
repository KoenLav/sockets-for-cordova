package cz.blocshop.socketsforcordova

import org.apache.cordova.CallbackContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SocketAdapterImpl : SocketAdapter {
    private val INPUT_STREAM_BUFFER_SIZE = 16 * 1024
    private val socket: Socket = Socket()
    private var openEventHandler: Consumer<Void>? = null
    private var openErrorEventHandler: Consumer<String>? = null
    private var dataConsumer: Consumer<ByteArray>? = null
    private var closeEventHandler: Consumer<Boolean>? = null
    private var errorEventHandler: Consumer<String>? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun open(host: String, port: Int, timeout: Int) {
        executor.submit {
            try {
                socket.soTimeout = timeout
                socket.connect(InetSocketAddress(host, port), 5000)
                invokeOpenEventHandler()
                submitReadTask()
            } catch (e: IOException) {
                Logging.Error(SocketAdapterImpl::class.java.name, "Error during connecting of socket", e.cause)
                invokeOpenErrorEventHandler(e.message)
            }
        }
    }

    override fun write(data: ByteArray, callbackContext: CallbackContext) {
        Thread {
            try {
                socket.getOutputStream().write(data)
                callbackContext.success()
            } catch (e: IOException) {
                e.printStackTrace()
                callbackContext.error(e.toString())
            }
        }.start()
    }

    @Throws(IOException::class)
    override fun shutdownWrite() {
        socket.shutdownOutput()
    }

    @Throws(IOException::class)
    override fun close() {
        socket.close()
        invokeCloseEventHandler(false)
    }

    @Throws(SocketException::class)
    override fun setOptions(options: SocketAdapterOptions) {
        options.keepAlive?.let { socket.keepAlive = it }
        options.oobInline?.let { socket.oobInline = it }
        options.soLinger?.let { socket.setSoLinger(true, it) }
        options.soTimeout?.let { socket.soTimeout = it }
        options.receiveBufferSize?.let { socket.receiveBufferSize = it }
        options.sendBufferSize?.let { socket.sendBufferSize = it }
        options.trafficClass?.let { socket.trafficClass = it }
    }

    override fun setOpenEventHandler(openEventHandler: Consumer<Void>) {
        this.openEventHandler = openEventHandler
    }

    override fun setOpenErrorEventHandler(openErrorEventHandler: Consumer<String>) {
        this.openErrorEventHandler = openErrorEventHandler
    }

    override fun setDataConsumer(dataConsumer: Consumer<ByteArray>) {
        this.dataConsumer = dataConsumer
    }

    override fun setCloseEventHandler(closeEventHandler: Consumer<Boolean>) {
        this.closeEventHandler = closeEventHandler
    }

    override fun setErrorEventHandler(errorEventHandler: Consumer<String>) {
        this.errorEventHandler = errorEventHandler
    }

    private fun submitReadTask() {
        executor.submit { runRead() }
    }

    private fun runRead() {
        var hasError = false
        try {
            runReadLoop()
        } catch (e: Throwable) {
            Logging.Error(SocketAdapterImpl::class.java.name, "Error during reading of socket input stream", e)
            hasError = true
            invokeExceptionHandler(e.message)
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Logging.Error(SocketAdapterImpl::class.java.name, "Error during closing of socket", e)
            } finally {
                invokeCloseEventHandler(hasError)
            }
        }
    }

    @Throws(IOException::class)
    private fun runReadLoop() {
        val buffer = ByteArray(INPUT_STREAM_BUFFER_SIZE)
        var bytesRead: Int
        while (socket.getInputStream().read(buffer).also { bytesRead = it } >= 0) {
            val data = if (buffer.size == bytesRead) buffer else Arrays.copyOfRange(buffer, 0, bytesRead)
            invokeDataConsumer(data)
        }
    }

    private fun invokeOpenEventHandler() {
        openEventHandler?.accept(null)
    }

    private fun invokeOpenErrorEventHandler(errorMessage: String?) {
        openErrorEventHandler?.accept(errorMessage)
    }

    private fun invokeDataConsumer(data: ByteArray) {
        dataConsumer?.accept(data)
    }

    private fun invokeCloseEventHandler(hasError: Boolean) {
        closeEventHandler?.accept(hasError)
    }

    private fun invokeExceptionHandler(errorMessage: String?) {
        errorEventHandler?.accept(errorMessage)
    }
}
