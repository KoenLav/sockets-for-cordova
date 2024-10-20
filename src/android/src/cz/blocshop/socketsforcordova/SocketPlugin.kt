/**
 * Copyright (c) 2015, Blocshop s.r.o.
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms are permitted
 * provided that the above copyright notice and this paragraph are
 * duplicated in all such forms and that any documentation,
 * advertising materials, and other materials related to such
 * distribution and use acknowledge that the software was developed
 * by the Blocshop s.r.o.. The name of the
 * Blocshop s.r.o. may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package cz.blocshop.socketsforcordova

import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaArgs
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import android.annotation.SuppressLint
import android.util.Log
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap

class SocketPlugin : CordovaPlugin() {

    private val socketAdapters = HashMap<String, SocketAdapter>()
    private val socketAdaptersPorts = HashMap<String, String>()

    @Throws(JSONException::class)
    override fun execute(action: String, args: CordovaArgs, callbackContext: CallbackContext): Boolean {
        return when (action) {
            "open" -> {
                open(args, callbackContext)
                true
            }
            "write" -> {
                write(args, callbackContext)
                true
            }
            "shutdownWrite" -> {
                shutdownWrite(args, callbackContext)
                true
            }
            "close" -> {
                close(args, callbackContext)
                true
            }
            "setOptions" -> {
                setOptions(args, callbackContext)
                true
            }
            else -> {
                callbackContext.error(String.format("SocketPlugin - invalid action:", action))
                false
            }
        }
    }

    @Throws(JSONException::class)
    private fun open(args: CordovaArgs, callbackContext: CallbackContext) {
        val socketKey = args.getString(0)
        val host = args.getString(1)
        val port = args.getInt(2)
        val timeout = args.getInt(3)
        val destination = host + port.toString()

        Log.d("SocketPlugin", "Open socket plugin")

        val socketAdapter = SocketAdapterImpl()

        socketAdapter.setCloseEventHandler(CloseEventHandler(socketKey))
        socketAdapter.setDataConsumer(DataConsumer(socketKey))
        socketAdapter.setErrorEventHandler(ErrorEventHandler(socketKey))
        socketAdapter.setOpenErrorEventHandler(OpenErrorEventHandler(callbackContext))
        socketAdapter.setOpenEventHandler(OpenEventHandler(socketKey, socketAdapter, callbackContext))

        if (socketAdaptersPorts.containsKey(destination)) {
            val existsSocketKey = socketAdaptersPorts[destination]
            val existsSocket = getSocketAdapter(existsSocketKey)

            try {
                if (existsSocket != null) {
                    existsSocket.close()
                    Log.d("SocketPlugin", "Old socket exists. Closing.")
                } else {
                    Log.d("SocketPlugin", "Old socket not exists.")
                }
            } catch (e: IOException) {
                Log.d("SocketPlugin", "Old socket closing error: " + e.message)
            }
        }

        socketAdapters[socketKey] = socketAdapter
        socketAdaptersPorts[destination] = socketKey

        socketAdapter.open(host, port, timeout)
    }

    @Throws(JSONException::class)
    private fun write(args: CordovaArgs, callbackContext: CallbackContext) {
        val socketKey = args.getString(0)
        val data = args.getJSONArray(1)

        val dataBuffer = ByteArray(data.length())

        for (i in dataBuffer.indices) {
            dataBuffer[i] = data.getInt(i).toByte()
        }

        val socket = getSocketAdapter(socketKey)

        if (socket != null) {
            socket.write(dataBuffer, callbackContext)
        } else {
            callbackContext.success()
        }
    }

    @Throws(JSONException::class)
    private fun shutdownWrite(args: CordovaArgs, callbackContext: CallbackContext) {
        val socketKey = args.getString(0)

        val socket = getSocketAdapter(socketKey)

        try {
            socket?.shutdownWrite()
            callbackContext.success()
        } catch (e: IOException) {
            callbackContext.error(e.toString())
        }
    }

    @Throws(JSONException::class)
    private fun close(args: CordovaArgs, callbackContext: CallbackContext) {
        val socketKey = args.getString(0)

        val socket = getSocketAdapter(socketKey)

        try {
            socket?.close()
            callbackContext.success()
        } catch (e: IOException) {
            callbackContext.error(e.toString())
        }
    }

    @Throws(JSONException::class)
    private fun setOptions(args: CordovaArgs, callbackContext: CallbackContext) {
        val socketKey = args.getString(0)
        val optionsJSON = args.getJSONObject(1)

        val socket = getSocketAdapter(socketKey)

        if (socket != null) {
            val options = SocketAdapterOptions()
            options.keepAlive = getBooleanPropertyFromJSON(optionsJSON, "keepAlive")
            options.oobInline = getBooleanPropertyFromJSON(optionsJSON, "oobInline")
            options.receiveBufferSize = getIntegerPropertyFromJSON(optionsJSON, "receiveBufferSize")
            options.sendBufferSize = getIntegerPropertyFromJSON(optionsJSON, "sendBufferSize")
            options.soLinger = getIntegerPropertyFromJSON(optionsJSON, "soLinger")
            options.soTimeout = getIntegerPropertyFromJSON(optionsJSON, "soTimeout")
            options.trafficClass = getIntegerPropertyFromJSON(optionsJSON, "trafficClass")

            try {
                socket.close()
                callbackContext.success()
            } catch (e: IOException) {
                callbackContext.error(e.toString())
            }
        }
    }

    @Throws(JSONException::class)
    private fun getBooleanPropertyFromJSON(jsonObject: JSONObject, propertyName: String): Boolean? {
        return if (jsonObject.has(propertyName)) jsonObject.getBoolean(propertyName) else null
    }

    @Throws(JSONException::class)
    private fun getIntegerPropertyFromJSON(jsonObject: JSONObject, propertyName: String): Int? {
        return if (jsonObject.has(propertyName)) jsonObject.getInt(propertyName) else null
    }

    private fun getSocketAdapter(socketKey: String?): SocketAdapter? {
        if (!socketAdapters.containsKey(socketKey)) {
            Log.d("SocketPlugin", "Adapter not exists")
            return null
        }

        return socketAdapters[socketKey]
    }

    private fun dispatchEvent(jsonEventObject: JSONObject) {
        this.webView.sendJavascript(String.format("window.Socket.dispatchEvent(%s);", jsonEventObject.toString()))
    }

    private inner class CloseEventHandler(private val socketKey: String) : Consumer<Boolean> {

        override fun accept(hasError: Boolean) {
            socketAdapters.remove(socketKey)

            try {
                val event = JSONObject()
                event.put("type", "Close")
                event.put("hasError", hasError)
                event.put("socketKey", socketKey)

                dispatchEvent(event)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private inner class DataConsumer(private val socketKey: String) : Consumer<ByteArray> {

        @SuppressLint("NewApi")
        override fun accept(data: ByteArray) {
            try {
                val event = JSONObject()
                event.put("type", "DataReceived")
                //event.put("data", new JSONArray(data)); NOT SUPPORTED IN API LEVEL LESS THAN 19
                event.put("data", JSONArray(toByteList(data)))
                event.put("socketKey", socketKey)

                dispatchEvent(event)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun toByteList(array: ByteArray): List<Byte> {
            val byteList = ArrayList<Byte>(array.size)
            for (i in array.indices) {
                byteList.add(array[i])
            }
            return byteList
        }
    }

    private inner class ErrorEventHandler(private val socketKey: String) : Consumer<String> {

        override fun accept(errorMessage: String) {
            try {
                val event = JSONObject()
                event.put("type", "Error")
                event.put("errorMessage", errorMessage)
                event.put("code", 0)
                event.put("socketKey", socketKey)

                dispatchEvent(event)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private inner class OpenErrorEventHandler(private val openCallbackContext: CallbackContext) : Consumer<String> {

        override fun accept(errorMessage: String) {
            try {
                val event = JSONObject()
                event.put("errorMessage", errorMessage)
                event.put("socketKey", "key")
                event.put("code", 0)
                openCallbackContext.error(event)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private inner class OpenEventHandler(
        private val socketKey: String,
        private val socketAdapter: SocketAdapter,
        private val openCallbackContext: CallbackContext
    ) : Consumer<Void> {

        override fun accept(voidObject: Void?) {
            openCallbackContext.success()
        }
    }
}
