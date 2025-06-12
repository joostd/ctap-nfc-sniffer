package nl.joostd.hcewebsocketrelay

import android.nfc.cardemulation.HostApduService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

class HostCardEmulatorService: HostApduService() {

    companion object {
        private const val TAG = "HceWebSocketService"
        private const val SERVER_URI = "ws://192.168.2.4:8765"
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val responseChannel = Channel<ByteArray>(Channel.CONFLATED)
    private var toneGenerator: ToneGenerator? = null


    override fun onCreate() {
        super.onCreate()
        try {
            // Use STREAM_MUSIC and set volume to 100 (max).
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator", e)
            toneGenerator = null
        }
        connectWebSocket()
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150) // Play for 150ms

        if (webSocket == null) {
            Log.e(TAG, "WebSocket not connected.")
            return byteArrayOf(0x6A.toByte(), 0x82.toByte()) // File not found / Connection error
        }

        Log.i(TAG, "Received APDU: ${commandApdu.toHexString()}")
        
        return runBlocking {
            try {
                // Clear any old responses and send the new command
                responseChannel.tryReceive().getOrNull() 
                webSocket?.send(commandApdu.toByteString())
                Log.i(TAG, "Sent APDU to server.")

                // Wait for the response from the WebSocket
                withTimeout(2000) { // 2-second timeout
                    val response = responseChannel.receive()
                    Log.i(TAG, "Relaying response from server: ${response.toHexString()}")
                    response
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out waiting for WebSocket response")
                // Return timeout error status word, e.g., 6A88 (Referenced data not found)
                byteArrayOf(0x6A.toByte(), 0x88.toByte())
            } catch (e: Exception) {
                Log.e(TAG, "Error during APDU processing", e)
                byteArrayOf(0x6F.toByte(), 0x00.toByte())
            }
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "Deactivated: reason $reason")
        toneGenerator?.release()
        toneGenerator = null
        serviceScope.cancel() // Cancel all coroutines
        webSocket?.close(NORMAL_CLOSURE_STATUS, "HCE Service Deactivated")
        client.dispatcher.executorService.shutdown()
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url(SERVER_URI).build()
        val listener = HceWebSocketListener()
        webSocket = client.newWebSocket(request, listener)
    }

    private inner class HceWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket connection opened")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val responseBytes = bytes.toByteArray()
            Log.i(TAG, "Received bytes response: ${responseBytes.toHexString()}")
            // Send the response to the waiting coroutine
            serviceScope.launch {
                responseChannel.send(responseBytes)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closing. Code: $code, Reason: $reason")
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            // Attempt to reconnect or handle the error
        }
    }

    // Extension function for easy byte array to hex string conversion for logging
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}

