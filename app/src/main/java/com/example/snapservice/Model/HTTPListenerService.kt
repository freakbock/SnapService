package com.example.snapservice.Model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.snapservice.R
import com.example.snapservice.View.CameraActivity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        registerReceiver()
        FileLogger.log(context, "Service create")
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
        unregisterReceiver()
        server?.stop()
    }

    private fun registerReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter("action_camera_image"))
    }

    private fun unregisterReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }


    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "action_camera_image") {
                val base64image = intent.getStringExtra(CameraActivity.IMAGE_DATA)
                // Обрабатываем полученный снимок
                if (base64image != null) {
                    formJsonPostQuert(base64image)
                }
            }
        }
    }

    private fun formJsonPostQuert(base64image: String){
        Thread{
            val jsonResponse = JSONObject()
            jsonResponse.put("key", KEY)
            jsonResponse.put("base64", base64image)

            Log.d("HTTPListenerService", jsonResponse.toString())
            FileLogger.log(
                context!!,
                "HTTPListenerService | ${jsonResponse.toString()}"
            )
            val sharedPreferences = context.getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )
            val url = sharedPreferences.getString("url", "")
            Log.d("HTTPListenerService", url!!)
            FileLogger.log(context, "HTTPListenerService | ${url!!}")
            if (url != null) {
                sendPostRequest(url, jsonResponse.toString())
                Log.d("HTTPListenerService", "Запрос отправлен")
                FileLogger.log(
                    context,
                    "HTTPListenerService | Запрос отправлен"
                )
            }
        }.start()
    }


    private val cameraLock = Object()
    private fun startServer() {
        Log.d("HTTPListenerService", "Starting service...")
        FileLogger.log(context, "HTTPListenerService | Starting service...")
        server = object : NanoHTTPD(8080) {
            override fun serve(session: IHTTPSession): Response {
                try{
                    Log.d("HTTPListenerService", "Есть коннект")
                    FileLogger.log(context, "HTTPListenerService | Есть подключение")
                    val uri = session.uri
                    if ("/snap" == uri && session.method == NanoHTTPD.Method.GET) {
                        val key = session.parms["key"]?.toString()
                        if (key != null) {
                            Log.d("HTTPListenerService", "Received key: $key")
                            FileLogger.log(context, "HTTPListenerService | Received key: $key")
                                synchronized(cameraLock) {
                                    KEY = key
                                    Handler(Looper.getMainLooper()).post {

                                        val intent = Intent(context, CameraActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)

//                                        photoHandler.takePicture { base64image ->
//                                            if(base64image != null){
//                                                val jsonResponse = JSONObject()
//                                                jsonResponse.put("key", key)
//                                                jsonResponse.put("base64", base64image)
//
//                                                Log.d("HTTPListenerService", jsonResponse.toString())
//                                                FileLogger.log(
//                                                    context,
//                                                    "HTTPListenerService | ${jsonResponse.toString()}"
//                                                )
//                                                val sharedPreferences = context.getSharedPreferences(
//                                                    "settings",
//                                                    Context.MODE_PRIVATE
//                                                )
//                                                val url = sharedPreferences.getString("url", "")
//                                                Log.d("HTTPListenerService", url!!)
//                                                FileLogger.log(context, "HTTPListenerService | ${url!!}")
//                                                if (url != null) {
//                                                    Thread{
//                                                        val isSuccess = sendPostRequest(url, jsonResponse.toString())
//                                                        if(isSuccess){
//                                                            isSuccessful = 1
//                                                        }
//                                                        else{
//                                                            isSuccessful = 3
//                                                        }
//                                                        isImageReceived = true
//                                                        Log.d("HTTPListenerService", "Запрос отправлен")
//                                                        FileLogger.log(
//                                                            context,
//                                                            "HTTPListenerService | Запрос отправлен"
//                                                        )
//                                                    }.start()
//                                                }
//                                                else{
//                                                    isSuccessful = 3
//                                                    isImageReceived = true
//                                                }
//                                            }
//                                            else{
//                                                isSuccessful = 2
//                                                isImageReceived = true
//                                            }
//                                        }
                                    }
                                }

                            return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Success")

                        }
                        else {
                            return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing key parameter")
                        }
                    } else {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
                    }
                }
                catch (e: Exception){
                    e.printStackTrace()
                    FileLogger.log(
                        context,
                        "HTTPListenerService | Ошибка: ${e.message}"
                    )
                    return newFixedLengthResponse(Response.Status.REQUEST_TIMEOUT, NanoHTTPD.MIME_PLAINTEXT, "Busy or Failed POST query")
                }
            }
        }
        try {
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            FileLogger.log(context, "HTTPListenerService | ${e.message}")
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
            FileLogger.log(context, "HTTPListenerService | ${e.message}")
            return false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private var KEY = "0"
        private const val NOTIFICATION_ID = 123
    }
}
