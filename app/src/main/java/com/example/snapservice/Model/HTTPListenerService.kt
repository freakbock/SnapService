package com.example.snapservice.Model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.snapservice.R
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HTTPListenerService : Service() {

    private var server: NanoHTTPD? = null
    private val photoHandler: PhotoHandler = PhotoHandler(this)
    val context: Context = this

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "channel_id"
            val channelName = "channel_id"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)
            val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val notificationBuilder = NotificationCompat.Builder(this, "channel_id")
            .setContentTitle("Your Service is Running")
            .setContentText("Your service is running in the background.")
            .setSmallIcon(R.drawable.logo)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return notificationBuilder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    private fun startServer() {
        Log.d("HTTPListenerService", "Starting service...")
        server = object : NanoHTTPD(8080) {
            override fun serve(session: IHTTPSession): Response {
                Log.d("HTTPListenerService", "Есть коннект")
                val uri = session.uri
                val jsonResponse = JSONObject()
                if ("/snap" == uri && session.method == NanoHTTPD.Method.GET) {
                    val key = session.parms["key"]?.toString()
                    if (key != null) {

                        Log.d("HTTPListenerService", "Received key: $key")
                        val handlerThread : HandlerThread = HandlerThread("CameraThread").apply { start() }
                        val cameraHandler : Handler = Handler(handlerThread.looper)
                        cameraHandler.post {
                            photoHandler.takePicture { base64image ->
                                jsonResponse.put("key", key)
                                jsonResponse.put("base64", base64image)

                                Log.d("HTTPListenerService", jsonResponse.toString())
                                val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                val url = sharedPreferences.getString("url", "")
                                Log.d("HTTPListenerService", url!!)
                                if (url != null) {
                                    sendPostRequest(url, jsonResponse.toString())
                                    Log.d("HTTPListenerService", "Запрос отправлен")
                                }
                            }
                        }
                        return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Success")
                    } else {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing key parameter")
                    }
                } else {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
                }
            }
        }
        try {
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun sendPostRequest(urlString: String, jsonData: String): Boolean {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        try {
            val outputStream = BufferedOutputStream(connection.outputStream)
            outputStream.write(jsonData.toByteArray())
            outputStream.flush()
            outputStream.close()
            val responseCode = connection.responseCode
            return responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 123
    }
}
