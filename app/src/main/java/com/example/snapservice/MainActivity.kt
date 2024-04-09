package com.example.snapservice

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.snapservice.Model.HTTPListenerService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val shared = getSharedPreferences("settings", Context.MODE_PRIVATE)
        Log.d("MainActivity", shared.getString("url", "url").toString())

        checkAndRequestPermissions()
    }

    private val PERMISSION_REQUEST_CODE = 100
    private val PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private fun checkAndRequestPermissions() {
        // Проверка наличия разрешений
        var allPermissionsGranted = true
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", permission.toString())
                allPermissionsGranted = false
                break
            }
        }

        // Если не все разрешения предоставлены, запросить их у пользователя
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
        else{
            StartService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                StartService()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Разрешения не предоставлены")
            .setMessage("Для использования приложения необходимо предоставить необходимые разрешения.")
            .setCancelable(false)
            .setNegativeButton("Выход") { dialogInterface: DialogInterface, i: Int ->
                finish() // Закрыть приложение при отказе предоставить разрешения
            }
            .setPositiveButton("Хорошо") {
                dialogInterface: DialogInterface, i: Int ->
                checkAndRequestPermissions()
                dialogInterface.dismiss()
            }
            .show()
    }

    fun StartService(){
        val serviceIntent = Intent(this, HTTPListenerService::class.java)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(serviceIntent)
        }
        else{
            startService(serviceIntent)
        }
    }

    fun SelectServerUrl(view: View){
        val urlEditText= findViewById<EditText>(R.id.serverUrl)
        val url = urlEditText.text.toString()
        if(url != null){
            val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("url", url)
            editor.apply()
            Toast.makeText(this, "Адрес веб сервера успешно обновлен", Toast.LENGTH_SHORT).show()
        }
        else{
            Toast.makeText(this, "Введите адрес веб сервера", Toast.LENGTH_SHORT).show()
        }
    }
}