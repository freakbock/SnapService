package com.example.snapservice.Model

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FileLogger {

    private const val LOG_FILE_NAME = "SnapService.txt"

    fun log(context: Context, message: String) {
        try {
            // Получаем директорию внутреннего хранилища приложения
            val directory = context.filesDir

            // Создаем файл внутри директории
            val file = File(directory, LOG_FILE_NAME)

            // Открываем файл для записи
            val outputStream = FileOutputStream(file, true)

            // Записываем сообщение в файл
            outputStream.write(message.toByteArray())
            outputStream.write("\n".toByteArray()) // Переход на новую строку
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

