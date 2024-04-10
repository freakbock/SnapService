package com.example.snapservice.Model

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.Camera
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
import android.os.Build
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
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import java.io.OutputStream

class PhotoHandler(private val context: Context) {

    fun takePicture(callback: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            takePictureWithCamera2(callback)
        } else {
            takePictureWithCamera1(callback)
        }
    }
    private val cameraLock = Object()
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun takePictureWithCamera2(callback: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]

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
                                        try{
                                            synchronized(cameraLock){
                                                val captureRequestBuilder =
                                                    camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                                captureRequestBuilder.addTarget(imageReader.surface)

                                                captureRequestBuilder.set(
                                                    CaptureRequest.CONTROL_MODE,
                                                    CameraCharacteristics.CONTROL_MODE_AUTO
                                                )
                                                captureRequestBuilder.set(
                                                    CaptureRequest.CONTROL_AE_MODE,
                                                    CameraCharacteristics.CONTROL_AE_MODE_ON
                                                )

                                                val captureRequest = captureRequestBuilder.build()
                                                session.capture(
                                                    captureRequest,
                                                    object : CameraCaptureSession.CaptureCallback() {
                                                        override fun onCaptureCompleted(
                                                            session: CameraCaptureSession,
                                                            request: CaptureRequest,
                                                            result: TotalCaptureResult
                                                        ) {
                                                            val buffer = imageReader.acquireLatestImage().planes[0].buffer
                                                            val bytes = ByteArray(buffer.capacity())
                                                            buffer.get(bytes)
                                                            callback(Base64.encodeToString(bytes, Base64.DEFAULT))
                                                            imageReader.close()
                                                            camera.close()
                                                        }
                                                    },
                                                    null
                                                )
                                            }
                                        }catch (e: Exception){
                                            e.printStackTrace()
                                            callback(null)
                                            FileLogger.log(context, "PhotoHandler | ${e.message}")
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        callback(null)
                                    }
                                },
                                null
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                            FileLogger.log(context, "PhotoHandler | ${e.message}")
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
            } catch (e: Exception) {
                e.printStackTrace()
                FileLogger.log(context, "PhotoHandler | ${e.message}")
                callback(null)
            }
        } else {
            // Обработка ошибки для версий API ниже 21 (например, Camera1 API)
            callback(null)
        }
    }

    private fun takePictureWithCamera1(callback: (String?) -> Unit) {
        val camera = Camera.open() ?: run {
            callback(null)
            return
        }

        try {
            val parameters = camera.parameters
            val supportedSizes = parameters.supportedPictureSizes
            val largestSize = Collections.max(supportedSizes) { size1, size2 ->
                size1.width * size1.height - size2.width * size2.height
            }

            parameters.setPictureSize(largestSize.width, largestSize.height)
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO // Можно настроить режим фокусировки, если нужно

            camera.parameters = parameters
            camera.startPreview()

            camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                if (data != null) {
                    val base64Image = Base64.encodeToString(data, Base64.DEFAULT)
                    callback(base64Image)
                } else {
                    callback(null)
                }
                camera.release()
            })
        } catch (e: Exception) {
            e.printStackTrace()
            FileLogger.log(context, "PhotoHandler | ${e.message}")
            callback(null)
            camera.release()
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
}