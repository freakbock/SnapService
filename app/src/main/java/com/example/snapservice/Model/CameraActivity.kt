package com.example.snapservice.Model
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream
import java.io.IOException

class CameraActivity : Activity() {

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
        const val IMAGE_DATA = "image_data"
    }
    var quality = "0"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        quality = intent.getStringExtra("quality")!!

        // Открываем камеру
        dispatchTakePictureIntent()
    }

    private var imageUri: Uri? = null
    private fun dispatchTakePictureIntent() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From the camera")
        }
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                // Изображение было успешно захвачено
                handleCapturedImage()
            } else if (resultCode == RESULT_CANCELED) {
                // Пользователь отменил съемку, можно выполнить соответствующие действия
                finish()
            } else {
                // Обработка других результатов
                finish()
            }
        }
    }

    private fun handleCapturedImage() {
        Thread {
            val imageBitmap = getImageFromUri(imageUri!!)

            // Сжимаем изображение до желаемого размера
            val compressedBitmap = compressBitmap(imageBitmap!!)

            // Преобразуем сжатое изображение в base64
            val base64Image = bitmapToBase64(compressedBitmap)

            // Отправляем base64 вещание
            val intent = Intent().apply {
                action = "action_camera_image"
                putExtra(IMAGE_DATA, base64Image)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            // Удаляем временное изображение
            deleteTemporaryImage()

            finish()
        }.start()
    }

    // Метод для сжатия изображения
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        var MAX_IMAGE_SIZE_BYTES = 1
        if(quality == "1"){
            MAX_IMAGE_SIZE_BYTES = 512 * 512
        }
        else if (quality == "2"){
            MAX_IMAGE_SIZE_BYTES = 128*128
        }
        else
        {
            MAX_IMAGE_SIZE_BYTES = 64*64
        }
        val outputStream = ByteArrayOutputStream()
        var quality = 50
        do {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 10
        } while (outputStream.size() > MAX_IMAGE_SIZE_BYTES && quality > 0)

        val compressedByteArray = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(compressedByteArray, 0, compressedByteArray.size)
    }

    // Метод для преобразования Bitmap в base64
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }

    private fun deleteTemporaryImage() {
        imageUri?.let { uri ->
            try {
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getImageFromUri(uri: Uri): Bitmap? {
        return try {
            // Открываем поток для чтения данных из файла
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Читаем данные из потока и создаем Bitmap
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri? {
        val context = applicationContext
        var savedImageURL: Uri?
        val imageFileName = "image.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DESCRIPTION, "My image")
        }

        val resolver = context.contentResolver
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
            savedImageURL = uri
        } ?: kotlin.run {
            savedImageURL = null
        }

        return null
    }
}
