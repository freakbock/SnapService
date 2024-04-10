package com.example.snapservice.View
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class CameraActivity : Activity() {

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
        const val IMAGE_DATA = "image_data"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            Thread{
                val imageBitmap = getImageFromUri(imageUri!!)

                saveImageToInternalStorage(imageBitmap!!)

                val byteArrayOutputStream = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()
                val base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
                val intent = Intent().apply {
                    action = "action_camera_image"
                    putExtra(IMAGE_DATA, base64Image)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                finish()
            }
                .start()
        }

        // Закрываем активность после получения снимка
        finish()
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
