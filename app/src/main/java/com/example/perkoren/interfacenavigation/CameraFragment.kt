package com.example.perkoren.interfacenavigation

/*
This code is based on https://github.com/tensorflow/tensorflow/blob/master/tensorflow/examples/android/src/org/tensorflow/demo/CameraConnectionFragment.java
*/

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.TextView
import com.example.perkoren.interfacenavigation.ImageClassifier.Companion.IMAGE_SIZE
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


private const val CONSEQUENT_MOVES = 3

class CameraFragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val lock = Any()
    private var runClassifier = false
    private var classifier: ImageClassifier? = null

    private val movementCounter: AtomicInteger = AtomicInteger()
    private lateinit var movementHandler: MovementHandler
    private var previousMove: Movement = Movement.CENTER

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    private var cameraId: String? = null
    private var textDisplay: TextView? = null
    private var textureView: AutoFitTextureView? = null
    private val lines: ArrayDeque<String> = ArrayDeque()
    private var captureSession: CameraCaptureSession? = null
    private var cameraDevice: CameraDevice? = null
    private var previewSize: Size? = null
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            Log.i(TAG, "camera device opened")
            cameraOpenCloseLock.release()
            cameraDevice = currentCameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
            val activity = activity
            activity?.finish()
        }
    }

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null
    private val cameraOpenCloseLock = Semaphore(1)


    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
        }
    }


    private val periodicClassify = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (runClassifier) {
                    classifyFrame()
                }
            }
            backgroundHandler!!.post(this)
        }
    }


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.camera_layout, null, true)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.textureView) as AutoFitTextureView
        textDisplay = view.findViewById(R.id.outputText) as TextView

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "Activity: ${activity!!.localClassName}")
        movementHandler = MovementHandler(activity!!)
        classifier = ImageClassifier(activity!!.assets)
        startBackgroundThread()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        if (textureView!!.isAvailable) {
            openCamera(textureView!!.width, textureView!!.height)
        } else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }


    private fun setUpCameraOutputs(width: Int, height: Int) {
        activity ?: return
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // // For still image captures, we use the largest available size.
                val largest = Collections.max(
                        Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)), CompareSizesByArea())
                imageReader = ImageReader.newInstance(
                        largest.width, largest.height, ImageFormat.JPEG, /*maxImages*/ 2)

                val displayRotation = activity!!.windowManager.defaultDisplay.rotation

                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }

                val displaySize = Point()
                activity!!.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                previewSize = chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largest)

                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView!!.setAspectRatio(previewSize!!.width, previewSize!!.height)
                } else {
                    textureView!!.setAspectRatio(previewSize!!.height, previewSize!!.width)
                }

                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access Camera", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, " Camera2API is used but not supported on the device", e)
        }

    }

    private fun openCamera(width: Int, height: Int) {
        activity ?: return
        Log.i(TAG, "openCamera permissions request")
        val permission = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        Log.i(TAG, "setting up camera outputs")
        setUpCameraOutputs(width, height)
        Log.i(TAG, "openCamera configure transform")
        configureTransform(width, height)
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open Camera", e)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        } catch (e: SecurityException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }


    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /** Closes the current [CameraDevice].  */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            if (null != imageReader) {
                imageReader!!.close()
                imageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread(HANDLE_THREAD_NAME)
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
        synchronized(lock) {
            runClassifier = true
        }
        backgroundHandler!!.postAtTime(periodicClassify, 10000)
    }

    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
            synchronized(lock) {
                runClassifier = false
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted when stopping background thread", e)
        }

    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView!!.surfaceTexture!!

            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)

            cameraDevice!!.createCaptureSession(
                    Arrays.asList(surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            if (null == cameraDevice) {
                                return
                            }

                            captureSession = cameraCaptureSession
                            try {
                                previewRequestBuilder!!.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                                previewRequest = previewRequestBuilder!!.build()
                                captureSession!!.setRepeatingRequest(
                                        previewRequest!!, captureCallback, backgroundHandler)
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to set up config to capture Camera", e)
                            }

                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            Log.e(TAG, "conf failed")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to preview Camera", e)
        }

    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize!!.height,
                    viewWidth.toFloat() / previewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
    }

    private fun classifyFrame() {
        if (classifier == null) {
            Log.e(TAG, "Uninitialized Classifier")
            return
        }
        if (activity == null) {
            Log.e(TAG, "Uninitialized activity")
            return
        }
        if (cameraDevice == null) {
            return
        }
        val bitmap = textureView!!.getBitmap(IMAGE_SIZE, IMAGE_SIZE)
        val result = classifier!!.recognizeImage(bitmap)
        val line = result.category + " - " + result.probability
        Log.i(TAG, line)
        lines.push(line)
        if (lines.size > 10) lines.removeLast()
        val linesToDisplay = lines.joinToString("\n")
        activity!!.runOnUiThread({
            textDisplay!!.text = linesToDisplay
        })

        mapToMovement(result)
        bitmap.recycle()
    }

    private fun mapToMovement(output: Output) {
        val currentMove = when (output.category) {
            "up" -> Movement.UP
            "left" -> Movement.LEFT
            "right" -> Movement.RIGHT
            "down" -> Movement.DOWN
            "center" -> Movement.CENTER
            else -> Movement.CENTER
        }
        if (currentMove == previousMove) {
            movementCounter.incrementAndGet()
        } else {
            movementCounter.set(0)
        }
        previousMove = currentMove

        if (movementCounter.get() == CONSEQUENT_MOVES) {
            Log.i(TAG, "New move: $currentMove")
            movementHandler.obtainMessage(currentMove.ordinal, currentMove).sendToTarget()
            movementCounter.set(0)
        }

    }

    private class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                    lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    enum class Movement {
        UP, LEFT, RIGHT, DOWN, CENTER
    }

    companion object {

        private val TAG = "InterfaceNavigation"
        private val HANDLE_THREAD_NAME = "CameraBackground"
        private val MAX_PREVIEW_WIDTH = 1920
        private val MAX_PREVIEW_HEIGHT = 1080

        private fun chooseOptimalSize(
                choices: Array<Size>,
                textureViewWidth: Int,
                textureViewHeight: Int,
                maxWidth: Int,
                maxHeight: Int,
                aspectRatio: Size): Size {

            val bigEnough = ArrayList<Size>()
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth
                        && option.height <= maxHeight
                        && option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            if (bigEnough.size > 0) {
                return Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }
    }
}

