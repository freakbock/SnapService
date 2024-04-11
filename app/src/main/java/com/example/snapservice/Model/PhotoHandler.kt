package com.example.snapservice.Model

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Range
import android.util.Size
import androidx.annotation.RequiresApi
import java.util.*

class PhotoHandler(private val context: Context) {

    fun takePicture(callback: (String?) -> Unit) {
        Handler(Looper.getMainLooper()).post{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                takePictureWithCamera2(callback)
            } else {
                takePictureWithCamera1(callback)
            }
        }
    }

    private fun takePictureWithCamera1(callback: (String?) -> Unit) {
        val camera = Camera.open() ?: run {
            callback(null)
            return
        }

        try {
            val parameters = camera.parameters
            parameters.pictureFormat = ImageFormat.JPEG
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO // Устанавливаем автофокус
            camera.parameters = parameters

            camera.startPreview()
            camera.autoFocus { success, _ ->
                if (success) {
                    camera.takePicture(null, null, object : Camera.PictureCallback {
                        override fun onPictureTaken(data: ByteArray?, camera: Camera?) {
                            camera?.release()
                            if (data != null) {
                                val base64Image = Base64.encodeToString(data, Base64.DEFAULT)
                                callback(base64Image)
                            } else {
                                callback(null)
                            }
                        }
                    })
                } else {
                    camera.release()
                    callback(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
            camera.release()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun takePictureWithCamera2(callback: (String?) -> Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                callback(null)
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
                            compareSizesByArea()
                        )

                        val imageReader = ImageReader.newInstance(
                            largestSize.width,
                            largestSize.height,
                            ImageFormat.JPEG,
                            1
                        )

                        imageReader.setOnImageAvailableListener({ reader ->
                            val image = reader.acquireLatestImage()
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)
                            val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                            callback(base64Image)
                            image.close()
                            camera.close()
                        }, null)

                        val surface = imageReader.surface

                        val captureRequestBuilder =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureRequestBuilder.addTarget(surface)

                        setupFPS(captureRequestBuilder)

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.capture(
                                            captureRequestBuilder.build(),
                                            null,
                                            null
                                        )
                                    } catch (e: CameraAccessException) {
                                        e.printStackTrace()
                                        callback(null)
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
            callback(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class compareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    val fpsRange: Range<Int> = Range(10,15)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setupFPS(builder: CaptureRequest.Builder) {
        if (fpsRange != null) {
            builder.set<Range<Int>>(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }
    }
}