package com.example.snapservice.Model

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import androidx.core.content.getSystemService
import java.io.OutputStream

class PhotoHandler(private val context: Context) {

    fun takePicture(callback: (String?) -> Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0] // Получаем ID первой доступной камеры (обычно это основная камера)
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        val map =
                            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val largestSize = Collections.max(
                            listOf(*map!!.getOutputSizes(ImageFormat.JPEG)),
                            CompareSizesByArea()
                        )

                        val imageReader = ImageReader.newInstance(
                            largestSize.width,
                            largestSize.height,
                            ImageFormat.JPEG,
                            1
                        )

                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    val captureRequestBuilder =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    captureRequestBuilder.addTarget(imageReader.surface)

                                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraCharacteristics.CONTROL_MODE_AUTO)
                                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraCharacteristics.CONTROL_AE_MODE_ON)
                                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 50) // Увеличиваем яркость

                                    val captureRequest = captureRequestBuilder.build()
                                    session.capture(
                                        captureRequest,
                                        object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult
                                            ) {
                                                val buffer =
                                                    imageReader.acquireLatestImage().planes[0].buffer
                                                val bytes = ByteArray(buffer.capacity())
                                                buffer.get(bytes)
                                                //saveImageToGallery(bytes)
                                                callback(Base64.encodeToString(bytes, Base64.DEFAULT))
                                                imageReader.close()
                                                camera.close()
                                            }
                                        },
                                        null
                                    )
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    callback(null)
                                }
                            },
                            null
                        )
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                        callback(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    callback(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    callback(null)
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            callback(null)
        }
    }

    private fun saveImageToGallery(imageBytes: ByteArray) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "my_image.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { imageUri ->
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    outputStream.write(imageBytes)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
}