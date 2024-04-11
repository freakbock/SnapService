package com.example.snapservice.Model

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object FileLogger {

    private const val LOG_FILE_NAME = "SnapService.txt"

    fun log(context: Context, message: String) {
        try {
            val file = getLogFile(context)
            FileOutputStream(file, true).use { outputStream ->
                outputStream.write(message.toByteArray())
                outputStream.write("\n".toByteArray())
                Log.d("FileLogger", "Запись в log")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLogFile(context: Context): File {
        return if (isExternalStorageWritable()) {
            File(context.getExternalFilesDir(null), LOG_FILE_NAME)
        } else {
            File(context.filesDir, LOG_FILE_NAME)
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }
}

