package com.shahzaib.mobispectral

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shahzaib.mobispectral.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
        makeDirectory(Utils.rawImageDirectory)
        makeDirectory(Utils.croppedImageDirectory)
        makeDirectory(Utils.processedImageDirectory)
        makeDirectory(Utils.hypercubeDirectory)
    }

    companion object {
        const val MOBISPECTRAL_APPLICATION = 0
        lateinit var fruitID: String
        lateinit var originalRGBBitmap: Bitmap
        lateinit var originalNIRBitmap: Bitmap
        lateinit var originalImageRGB: String
        lateinit var originalImageNIR: String
        lateinit var processedImageRGB: String
        lateinit var processedImageNIR: String
        var croppedImageRGB: String = ""
        var croppedImageNIR: String = ""
        var actualLabel: String = ""
        var predictedLabel: String = ""
        var normalizationTime: String = " s"
        var reconstructionTime: String = " s"
        var classificationTime: String = " ms"
        lateinit var tempRGBBitmap: Bitmap
        lateinit var tempRectangle: Rect
    }
}