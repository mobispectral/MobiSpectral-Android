package com.shahzaib.mobispectral.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.database.Cursor
import android.graphics.*
import android.hardware.camera2.*
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.getPreviewOutputSize
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shahzaib.mobispectral.MainActivity
import com.shahzaib.mobispectral.R
import com.shahzaib.mobispectral.Utils
import com.shahzaib.mobispectral.compressImage
import com.shahzaib.mobispectral.databinding.FragmentCameraBinding
import com.shahzaib.mobispectral.readImage
import com.shahzaib.mobispectral.saveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraFragment: Fragment() {
    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    private lateinit var cameraIdRGB: String
    private lateinit var cameraIdNIR: String

    private lateinit var sharedPreferences: SharedPreferences
    private var mobiSpectralApplicationID = 0
    private var offlineMode = false

    private val myActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.clipData == null) {
                if (cameraIdNIR != "OnePlus") {
                    generateAlertBox(requireContext(), "Only One Image Selected", "Cannot select 1 image, Select Two images.\nFirst image RGB, Second image NIR")
                }
                else {
                    // If we have to select one image.
                    val nirUri: Uri? = result.data!!.data
                    nirAbsolutePath = nirUri?.let { getRealPathFromURI(it) }.toString()
                    Log.i("Images Opened Path", "NIR Path: $nirAbsolutePath")
                }
            }
            else {
                if (result.data?.clipData?.itemCount == 2) {
                    var rgbFile = getRealPathFromURI(result.data!!.clipData?.getItemAt(0)?.uri!!)
                    var nirFile = getRealPathFromURI(result.data!!.clipData?.getItemAt(1)?.uri!!)

                    if (rgbFile.contains("NIR")) {
                        val tempFile = rgbFile
                        rgbFile = nirFile
                        nirFile = tempFile
                    }

                    var rgbBitmap = readImage(rgbFile)
                    var nirBitmap = readImage(nirFile)

                    MainActivity.originalImageRGB = rgbFile
                    MainActivity.originalImageNIR = nirFile

                    if (nirBitmap.width > rgbBitmap.width && nirBitmap.height > rgbBitmap.height) {
                        val tempBitmap = rgbBitmap
                        rgbBitmap = nirBitmap
                        nirBitmap = tempBitmap
                    }

                    rgbBitmap = compressImage(rgbBitmap)
                    nirBitmap = compressImage(nirBitmap)

                    val rgbBitmapOutputFile = createFile("RGB")
                    val nirBitmapOutputFile = File(rgbBitmapOutputFile.toString().replace("RGB", "NIR"))
                    rgbAbsolutePath = rgbBitmapOutputFile.absolutePath
                    nirAbsolutePath = nirBitmapOutputFile.absolutePath
                    Log.i("Images Opened Path", "RGB Path: $rgbAbsolutePath, NIR Path: $nirAbsolutePath")

                    lifecycleScope.launch {
                        saveImage(rgbBitmap, rgbBitmapOutputFile)
                        saveImage(nirBitmap, nirBitmapOutputFile)

                        navController.navigate(
                            CameraFragmentDirections.actionCameraToJpegViewer(rgbAbsolutePath, nirAbsolutePath)
                        )
                    }
                }
                else {
                    generateAlertBox(requireContext(),"Number of images exceeded 2", "Cannot select more than 2 images.\nFirst image RGB, Second image NIR")
                }
            }
        }
        if (result.resultCode == Activity.RESULT_CANCELED) {
            generateAlertBox(requireContext(), "No Images Selected", "Select Images again.\nFirst image RGB, Second image NIR")
        }
    }

    fun generateAlertBox(context: Context, title: String, text: String) {
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
        alertDialogBuilder.setMessage(text)
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setCancelable(false)
        if (title == "Information")
            alertDialogBuilder.setPositiveButton("Okay") { dialog, _ -> dialog?.cancel() }
        else
            alertDialogBuilder.setPositiveButton("Reload") { _, _ -> startMyActivityForResult() }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private val cameraSurfaceHolderCallback = object: SurfaceHolder.Callback {
        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceCreated(holder: SurfaceHolder) {
            // Selects appropriate preview size and configures view finder
            val previewSize = getPreviewOutputSize(fragmentCameraBinding.viewFinder.display,
                characteristics, SurfaceHolder::class.java)
            // fragmentCameraBinding.viewFinder.setAspectRatio(previewSize.width, previewSize.height)
            holder.setFixedSize(previewSize.width, previewSize.height)

            Log.i("Preview Size", "AutoFitSurface Holder: Width ${fragmentCameraBinding.viewFinder.width}, Height ${fragmentCameraBinding.viewFinder.height}")

            // To ensure that size is set, initialize camera in the view's thread
            view?.post { initializeCamera() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        sharedPreferences = requireActivity().getSharedPreferences("mobispectral_preferences", Context.MODE_PRIVATE)
        mobiSpectralApplicationID = when(sharedPreferences.getString("application", "Organic Identification")!!) {
            else -> MainActivity.MOBISPECTRAL_APPLICATION
        }
        offlineMode = sharedPreferences.getBoolean("offline_mode", false)
        cameraIdRGB = Utils.getCameraIDs(requireContext(), mobiSpectralApplicationID).first
        cameraIdNIR = Utils.getCameraIDs(requireContext(), mobiSpectralApplicationID).second

        Log.i("Camera IDs", "RGB: $cameraIdRGB, NIR: $cameraIdNIR")
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentCameraBinding.information.setOnClickListener {
            generateAlertBox(requireContext(),"Information", resources.getString(R.string.capture_information_string))
        }

        if (cameraIdNIR == "OnePlus" || offlineMode)
            startMyActivityForResult()

        fragmentCameraBinding.viewFinder.holder.addCallback(cameraSurfaceHolderCallback)

        fragmentCameraBinding.Title.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                navController.navigate(CameraFragmentDirections.actionCameraToApplicationsTitle())
            }
        }

        fragmentCameraBinding.reloadButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                navController.navigate(CameraFragmentDirections.actionCameraToApplications())
            }
        }
    }
    override fun onResume() {
        super.onResume()
        fragmentCameraBinding.viewFinder.holder.addCallback(cameraSurfaceHolderCallback)
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        // Open the selected camera
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        val size = if (cameraIdNIR == "OnePlus") Size(Utils.torchHeight, Utils.torchWidth) else Size(Utils.previewWidth, Utils.previewHeight)
        // val size = Size(Utils.previewWidth, Utils.previewHeight)

        imageReader = ImageReader.newInstance(size.width, size.height, args.pixelFormat, IMAGE_BUFFER_SIZE)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(fragmentCameraBinding.viewFinder.holder.surface, imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply { addTarget(fragmentCameraBinding.viewFinder.holder.surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO) }

        // This will keep sending the capture request as frequently as possible until the
        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        if (args.cameraId == cameraIdNIR) {
            fragmentCameraBinding.captureButton.performClick()
            fragmentCameraBinding.captureButton.isPressed = true
            fragmentCameraBinding.captureButton.invalidate()
        }

        // Listen to the capture button
        if (args.cameraId == cameraIdRGB) {
            fragmentCameraBinding.captureButton.setOnClickListener {
                if (args.cameraId == cameraIdNIR) {
                    fragmentCameraBinding.captureButton.isPressed = false
                    fragmentCameraBinding.captureButton.invalidate()
                }
                // Disable click listener to prevent multiple requests simultaneously in flight
                it.isEnabled = false
                fragmentCameraBinding.timer.visibility = View.VISIBLE

                val beepingTone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

                object: CountDownTimer(3000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val secondsRemaining = millisUntilFinished / 1000
                        fragmentCameraBinding.timer.text = "$secondsRemaining"
                        beepingTone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    }
                    override fun onFinish() {
                        fragmentCameraBinding.timer.visibility = View.INVISIBLE
                        Utils.vibrate(requireContext())
                        savePhoto(args.cameraId)
                    }
                }.start()
                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
            }
        }
        else {
            savePhoto(args.cameraId)
        }
    }

    private fun savePhoto(cameraId: String) {
        // Perform I/O heavy operations in a different scope
        lifecycleScope.launch(Dispatchers.IO) {
            takePhoto().use { result ->
                Log.d(TAG, "Result received: $result")

                // Save the result to disk
                val output = saveResult(result)
                lifecycleScope.launch(Dispatchers.Main) {
                    if (cameraId == cameraIdRGB){
                        when (cameraIdNIR) {
                            "OnePlus" -> navController.navigate(CameraFragmentDirections.actionCameraToJpegViewer(rgbAbsolutePath, nirAbsolutePath))
                            else -> navController.navigate(CameraFragmentDirections.actionCameraFragmentSelf(cameraIdNIR, ImageFormat.JPEG))
                        }
                    }
                    else
                        navController.navigate(CameraFragmentDirections.actionCameraToJpegViewer(rgbAbsolutePath, output.absolutePath))
                }
            }
        }
    }

    private fun startMyActivityForResult() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        lifecycleScope.launch(Dispatchers.Main) {
            myActivityResultLauncher.launch(galleryIntent)
        }
    }

    private fun getRealPathFromURI(contentUri: Uri): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor = requireContext().contentResolver.query(contentUri, proj, null, null, null)!!
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val absolutePath = cursor.getString(columnIndex)
        cursor.close()
        return absolutePath
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, cameraId: String, handler: Handler? = null):
            CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object: CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    @Suppress("DEPRECATION")
    private suspend fun createCaptureSession(device: CameraDevice, targets: List<Surface>, handler: Handler? = null):
            CameraCaptureSession = suspendCoroutine { cont ->
        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp} W ${image.width}, H ${image.height}")

            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            .apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), object: CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)

                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                // the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {
                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()

                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp
                        ) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}, W ${image.width}, H ${image.height}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) { imageQueue.take().close() }
                        Log.d(TAG, "Capture Request DIMENSIONS: width ${image.width} Height: ${image.height}")

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(image, result, imageReader.imageFormat))
                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)
    }

    private fun isDark(bitmap: Bitmap): Boolean {
        val histogram = IntArray(256)

        for (i in 0..255) {
            histogram[i] = 0
        }

        var dark = false
        val darkThreshold = 0.25F
        var darkPixels = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (color in pixels) {
            val r: Int = Color.red(color)
            val g: Int = Color.green(color)
            val b: Int = Color.blue(color)
            val brightness = (0.2126*r + 0.7152*g + 0.0722*b).toInt()
            histogram[brightness]++
        }

        for (i in 0..9)
            darkPixels += histogram[i]
        if (darkPixels > (bitmap.height * bitmap.width) * darkThreshold) {
            dark = true
        }
        Log.i("Dark", "DarkPixels: $darkPixels")
        return dark
    }

    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {
            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                var rotatedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
                val correctionMatrix = Matrix().apply { postRotate(-90F); postScale(-1F, 1F); }
                val nirCameraID = Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).second
                rotatedBitmap = Bitmap.createBitmap(rotatedBitmap, 0, 0, rotatedBitmap.width,
                    rotatedBitmap.height, if (nirCameraID == "OnePlus") Matrix().apply { postRotate(90F) } else correctionMatrix, false)
                val stream = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val rotatedBytes = stream.toByteArray()
                Log.i("Save Photo", "Bitmap Size: W ${rotatedBitmap.width} H ${rotatedBitmap.height}, byte size: ${bytes.size}, rotated Bytes Size: ${rotatedBytes.size} buffer Size: $buffer")

                try {
                    val nir = if (args.cameraId == cameraIdNIR) "NIR" else "RGB"

                    if (isDark(rotatedBitmap)) {
                        Log.i("Dark", "The bitmap is too dark")
                        fragmentCameraBinding.illumination.text = resources.getString(R.string.formatted_illumination_string, "Inadequate")
                        fragmentCameraBinding.illumination.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
                        Toast.makeText(context, "The bitmap is too dark", Toast.LENGTH_SHORT).show()
                    }
                    else {
                        fragmentCameraBinding.illumination.text = resources.getString(R.string.formatted_illumination_string, "Adequate")
                        fragmentCameraBinding.illumination.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_secondary))
                    }
                    val output = createFile(nir)
                    FileOutputStream(output).use { it.write(rotatedBytes) }
                    cont.resume(output)
                    Log.i("Filename", output.toString())
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
        private lateinit var fileFormat: String
        lateinit var rgbAbsolutePath: String
        lateinit var nirAbsolutePath: String

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(val image: Image, val metadata: CaptureResult, val format: Int): Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(nir: String): File {
            val externalStorageDirectory = Environment.getExternalStorageDirectory().toString()
            val rootDirectory = File(externalStorageDirectory, "/MobiSpectral")
            val imageDirectory = File(rootDirectory, "/${Utils.rawImageDirectory}")

            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            fileFormat = sdf.format(Date())
            val output = File(imageDirectory, "IMG_${fileFormat}_$nir.jpg")
            if (nir == "RGB")
                rgbAbsolutePath = output.absolutePath
            return output
        }
    }
}