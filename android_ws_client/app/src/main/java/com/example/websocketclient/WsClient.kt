package com.example.websocketclient

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface WsCallback {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onError(errorMessage: String)
    fun onMessageReceived(text: String)
}

/**
 * Manages WebSocket network connections using OkHttp.
 */
class WsClient(private val callback: WsCallback) {

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null

    fun connect(ip: String, port: Int) {
        disconnect()

        client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val endpointUrl = "ws://$ip:$port"
        val request = Request.Builder()
            .url(endpointUrl)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                callback.onMessageReceived(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                callback.onDisconnected(reason.ifEmpty { "Соединение закрывается..." })
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callback.onDisconnected(reason.ifEmpty { "Отключено" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callback.onError(t.localizedMessage ?: "Ошибка подключения")
            }
        })
    }

    fun sendMessage(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Disconnect requested")
            webSocket = null
            client?.dispatcher?.executorService?.shutdown()
            client = null
        } catch (_: Exception) {
        }
    }
}
