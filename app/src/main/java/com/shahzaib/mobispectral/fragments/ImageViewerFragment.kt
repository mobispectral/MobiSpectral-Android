package com.shahzaib.mobispectral.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.android.camera.utils.GenericListAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.shahzaib.mobispectral.*
import com.shahzaib.mobispectral.Utils.cropImage
import com.shahzaib.mobispectral.databinding.FragmentImageviewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.BufferedInputStream
import java.io.File

class ImageViewerFragment: Fragment() {
    private val correctionMatrix = Matrix().apply { postRotate(-90F); }

    /** AndroidX navigation arguments */
    private val args: ImageViewerFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }
    private lateinit var sharedPreferences: SharedPreferences
    private var _fragmentImageViewerBinding: FragmentImageviewerBinding? = null
    private val fragmentImageViewerBinding get() = _fragmentImageViewerBinding!!
    /** Default Bitmap decoding options */
    private val bitmapOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    /** Data backing our Bitmap viewpager */
    private val bitmapList: MutableList<Bitmap> = mutableListOf()
    private var bitmapsWidth = Utils.torchWidth
    private var bitmapsHeight = Utils.torchHeight

    private var topCrop = -1F
    private var bottomCrop = -1F
    private var leftCrop = -1F
    private var rightCrop = -1F
    private val loadingDialogFragment = LoadingDialogFragment()

    private var advancedControlOption: Boolean = false

    private fun imageViewFactory() = ImageView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    private fun boundingBox(left: Float, right: Float, top: Float, bottom: Float,
                            canvas: Canvas, view: ImageView, bitmapOverlay: Bitmap, position: Int) {
        val paint = Paint()
        paint.color = Color.argb(255, 0, 0, 0)
        paint.strokeWidth = 2.5F
        paint.style = Paint.Style.STROKE

        Log.i("Crop Location", "L: $left, R: $right, T: $top, B: $bottom")

        canvas.drawRect(left-2.5F, top-2.5F, right+2.5F, bottom+2.5F, paint)
        view.setImageBitmap(bitmapOverlay)
        if (bitmapOverlay.width > Utils.boundingBoxWidth*2 && bitmapOverlay.height > Utils.boundingBoxHeight*2 && position == 0) {
            MainActivity.tempRGBBitmap = bitmapOverlay
            MainActivity.tempRectangle = Rect(bottom.toInt(), left.toInt(), right.toInt(), top.toInt())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("KotlinConstantConditions")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        OpenCVLoader.initDebug()
        sharedPreferences = requireActivity().getSharedPreferences("mobispectral_preferences", Context.MODE_PRIVATE)
        advancedControlOption = when (sharedPreferences.getString("option", getString(R.string.advanced_option_string))!!) {
            getString(R.string.advanced_option_string) -> true
            getString(R.string.simple_option_string) -> false
            else -> true
        }
        LoadingDialogFragment.text = getString(R.string.normalizing_image_string)
        loadingDialogFragment.isCancelable = false
        makeFolderInRoot(Utils.MobiSpectralPath, requireContext())
        var firstTap = false

        _fragmentImageViewerBinding = FragmentImageviewerBinding.inflate(inflater, container, false)
        fragmentImageViewerBinding.viewpager.apply {
            offscreenPageLimit=2
            adapter = GenericListAdapter(bitmapList,
                itemViewFactory = { imageViewFactory() }) { view, item, position ->
                view as ImageView
                view.scaleType = ImageView.ScaleType.FIT_XY
                val bitmapOverlay = Bitmap.createBitmap(item.width, item.height, item.config)
                val canvas = Canvas(bitmapOverlay)

                canvas.drawBitmap(item, Matrix(), null)
                if (!advancedControlOption)
                    Handler(Looper.getMainLooper()).postDelayed({
                        boundingBox(item.width/2 - Utils.boundingBoxWidth, item.width/2 + Utils.boundingBoxWidth,
                            item.height/2 - Utils.boundingBoxHeight, item.height/2 + Utils.boundingBoxHeight,
                            canvas, view, bitmapOverlay, position)
                    }, 100)
                else if (advancedControlOption && position == 0) {
                    MainActivity.tempRGBBitmap = bitmapOverlay
                }

                view.setOnTouchListener { v, event ->
                    canvas.drawBitmap(item, Matrix(), null)

                    var clickedX = ((event!!.x / v!!.width) * bitmapsWidth).toInt()
                    var clickedY = ((event.y / v.height) * bitmapsHeight).toInt()

                    // Make sure the bounding box doesn't go outside the bounds of the image
                    if (clickedX + Utils.boundingBoxWidth > item.width)
                        clickedX = (item.width - Utils.boundingBoxWidth).toInt()
                    if (clickedY + Utils.boundingBoxHeight > item.width)
                        clickedY = (item.height - Utils.boundingBoxHeight).toInt()

                    if (clickedX - Utils.boundingBoxWidth < 0)
                        clickedX = (0 + Utils.boundingBoxWidth).toInt()
                    if (clickedY - Utils.boundingBoxHeight < 0)
                        clickedY = (0 + Utils.boundingBoxHeight).toInt()

                    Log.i("Box Added", "X: $clickedX ($bitmapsWidth), Y: $clickedY ($bitmapsHeight)")

                    if (!firstTap) {
                        leftCrop = item.width/2 - Utils.boundingBoxWidth
                        topCrop = item.height/2 - Utils.boundingBoxHeight
                        rightCrop = item.width/2 + Utils.boundingBoxWidth
                        bottomCrop = item.height/2 + Utils.boundingBoxHeight
                        firstTap = true
                    }
                    else {
                        leftCrop = clickedX - Utils.boundingBoxWidth
                        topCrop = clickedY - Utils.boundingBoxHeight
                        rightCrop = clickedX + Utils.boundingBoxWidth
                        bottomCrop = clickedY + Utils.boundingBoxHeight
                    }
                    boundingBox(leftCrop, rightCrop, topCrop, bottomCrop, canvas, view, bitmapOverlay, position)

                    false
                }
                Glide.with(view).load(item).into(view)
            }
        }
        TabLayoutMediator(fragmentImageViewerBinding.tabLayout,
            fragmentImageViewerBinding.viewpager) { tab, position ->
            tab.text = if (position%2==0) "RGB" else "NIR"
        }.attach()
        return fragmentImageViewerBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentImageViewerBinding.Title.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                navController.navigate(
                    ImageViewerFragmentDirections
                        .actionImageViewerFragmentToApplicationTitle()
                )
            }
        }

        fragmentImageViewerBinding.reloadButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                navController.navigate(
                    ImageViewerFragmentDirections
                        .actionImageViewerFragmentToCameraFragment(
                            Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).first, ImageFormat.JPEG)
                )
            }
        }

        fragmentImageViewerBinding.information.setOnClickListener {
            CameraFragment().generateAlertBox(requireContext(), "Information", getString(R.string.image_viewer_information_string))
        }

        loadingDialogFragment.show(childFragmentManager, LoadingDialogFragment.TAG)
    }

    override fun onStart() {
        super.onStart()
        val offlineMode = sharedPreferences.getBoolean("offline_mode", false)

        lifecycleScope.launch(Dispatchers.IO) {
            // Load input image file
            val (bufferRGB, bufferNIR) = loadInputBuffer()

            // Load the main JPEG image
            var rgbImageBitmap = decodeBitmap(bufferRGB, bufferRGB.size, true)
            var nirImageBitmap = decodeBitmap(bufferNIR, bufferNIR.size, false)
            Log.i("Bitmap Size", "Decoded RGB: ${rgbImageBitmap.width} x ${rgbImageBitmap.height}, Decoded NIR: ${nirImageBitmap.width} x ${nirImageBitmap.height}")

            if (rgbImageBitmap.width != nirImageBitmap.width && rgbImageBitmap.height != nirImageBitmap.height)
                rgbImageBitmap = Utils.fixedAlignment(rgbImageBitmap)

            Log.i("Bitmap Size", "Decoded RGB: ${rgbImageBitmap.width} x ${rgbImageBitmap.height}, Decoded NIR: ${nirImageBitmap.width} x ${nirImageBitmap.height}")

            bitmapsWidth = rgbImageBitmap.width
            bitmapsHeight = rgbImageBitmap.height
            val viewpagerThread = Thread {
                addItemToViewPager(fragmentImageViewerBinding.viewpager, rgbImageBitmap, 0)
                addItemToViewPager(fragmentImageViewerBinding.viewpager, nirImageBitmap, 1)
            }

            viewpagerThread.start()
            try { viewpagerThread.join() }
            catch (exception: InterruptedException) { exception.printStackTrace() }

            loadingDialogFragment.dismissDialog()

            val rgbImage = File(args.filePath)
            val directoryPath = rgbImage.absolutePath.split(System.getProperty("file.separator")!!)
            val rgbImageFileName = directoryPath[directoryPath.size-1]
            val nirImage = File(args.filePath2)
            val nirDirectoryPath = nirImage.absolutePath.split(System.getProperty("file.separator")!!)
            val nirImageFileName = nirDirectoryPath[nirDirectoryPath.size-1]
            saveProcessedImages(requireContext(), rgbImageBitmap, nirImageBitmap, rgbImageFileName, nirImageFileName, Utils.processedImageDirectory)

            fragmentImageViewerBinding.button.setOnClickListener {
                // if crop isn't initialized for simple mode
                if (leftCrop == -1F && topCrop == -1F && !advancedControlOption) {
                    leftCrop = rgbImageBitmap.width/2 - Utils.boundingBoxWidth
                    topCrop = rgbImageBitmap.height/2 - Utils.boundingBoxWidth
                }
                Log.i("Cropped Image", "$leftCrop $topCrop")
                // if the app is asked to crop the image
                if (leftCrop != -1F && topCrop != -1F) {
                    rgbImageBitmap = cropImage(rgbImageBitmap, leftCrop, topCrop)
                    nirImageBitmap = cropImage(nirImageBitmap, leftCrop, topCrop)
                    Log.i("Cropped Image", "${rgbImageBitmap.width} ${rgbImageBitmap.height}")
                    Log.i("Cropped Image", "${nirImageBitmap.width} ${nirImageBitmap.height}")
                    saveProcessedImages(requireContext(), rgbImageBitmap, nirImageBitmap, rgbImageFileName, nirImageFileName, Utils.croppedImageDirectory)
                }

                // addItemToViewPager(fragmentImageViewerBinding.viewpager, rgbImageBitmap, 2)
                // addItemToViewPager(fragmentImageViewerBinding.viewpager, nirImageBitmap, 3)

                MainActivity.originalRGBBitmap = rgbImageBitmap
                MainActivity.originalNIRBitmap = nirImageBitmap

                lifecycleScope.launch(Dispatchers.Main) {
                    navController.navigate(ImageViewerFragmentDirections.actionImageViewerFragmentToReconstructionFragment2())
                }
            }
        }
    }

    /** Utility function used to read input file into a byte array */
    private fun loadInputBuffer(): Pair<ByteArray, ByteArray> {
        val rgbFile = File(args.filePath)
        val nirFile = File(args.filePath2)
        val rgbImage = BufferedInputStream(rgbFile.inputStream()).let { stream ->
            ByteArray(stream.available()).also {
                stream.read(it)
                stream.close()
            }
        }
        val nirImage = BufferedInputStream(nirFile.inputStream()).let { stream ->
            ByteArray(stream.available()).also {
                stream.read(it)
                stream.close()
            }
        }
        return Pair(rgbImage, nirImage)
    }

    /** Utility function used to add an item to the viewpager and notify it, in the main thread */
    private fun addItemToViewPager(view: ViewPager2, item: Bitmap, position: Int) = view.post {
        bitmapList.add(item)
        view.adapter!!.notifyItemChanged(position)
    }

    @Suppress("unused")
    private fun whiteBalance(rgbBitmap: Bitmap): Bitmap {
        val startTime = System.currentTimeMillis()
        val whiteBalanceModel = WhiteBalance(requireContext())
        val whiteBalancedBitmap = whiteBalanceModel.whiteBalance(rgbBitmap)
        val endTime = System.currentTimeMillis()
        MainActivity.normalizationTime = "${((endTime - startTime).toFloat() / 1000.0F)} s"
        return whiteBalancedBitmap
    }

    /** Utility function used to decode a [Bitmap] from a byte array */
    private fun decodeBitmap(buffer: ByteArray, length: Int, isRGB: Boolean): Bitmap {
        var bitmap: Bitmap

        // Load bitmap from given buffer
        val decodedBitmap = BitmapFactory.decodeByteArray(buffer, 0, length, bitmapOptions)
        if (isRGB) RGB_DIMENSION = Pair(decodedBitmap.width, decodedBitmap.height)

        if (isRGB){
            bitmap = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, null, false)
//            val whiteBalancingThread = Thread {
//                bitmap = whiteBalance(bitmap)
//            }
//            whiteBalancingThread.start()
//            try { whiteBalancingThread.join() }
//            catch (exception: InterruptedException) { exception.printStackTrace() }
        }
        else {
            bitmap = if (decodedBitmap.width < decodedBitmap.height)
                Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, correctionMatrix, false)
            else
                Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, null, false)
            if (Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).second == "OnePlus")
                bitmap = Bitmap.createScaledBitmap(bitmap, RGB_DIMENSION.first, RGB_DIMENSION.second, true)
        }
        // Transform bitmap orientation using provided metadata
        return bitmap
    }

    companion object {
        private lateinit var RGB_DIMENSION: Pair<Int, Int>
    }
}