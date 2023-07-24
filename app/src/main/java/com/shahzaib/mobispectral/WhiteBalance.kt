package com.shahzaib.mobispectral

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color.rgb
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import kotlin.math.roundToInt

class WhiteBalance(context: Context) {
    private var modelAwb: Module? = null
    private var modelS: Module? = null
    private var modelT: Module? = null
    private var outputAwb: Tensor? = null
    private var outputT: Tensor? = null
    private var outputS: Tensor? = null

    private var mean = floatArrayOf(0.0f, 0.0f, 0.0f)
    private var std = floatArrayOf(1.0f, 1.0f, 1.0f)

    init {
        modelAwb = Module.load(Utils.assetFilePath(context, "mobile_awb.pt"))
        modelS = Module.load(Utils.assetFilePath(context, "mobile_s.pt"))
        modelT = Module.load(Utils.assetFilePath(context, "mobile_t.pt"))
    }

    private fun deepWB(image: Tensor): Tensor {
        val inputs: IValue = IValue.from(image)
        outputAwb = modelAwb?.forward(inputs)?.toTensor()!!
        outputT = modelT?.forward(inputs)?.toTensor()!!
        outputS = modelS?.forward(inputs)?.toTensor()!!

        return colorTempInterpolate(outputT!!, outputS!!)
    }

    /*
    * This function does the following python equivalent operation:
    * I_D = I_T * g_D + I_S * (1 - g_D)
    * */
    private fun multiplyAndAddTensors(tensor1: Tensor, tensor2: Tensor, scalar: Float): Tensor {
        val float1 = tensor1.dataAsFloatArray
        val float2 = tensor2.dataAsFloatArray
        val resulting = FloatArray(float1.size)

        for (i in resulting.indices) {
            resulting[i] = (scalar * float1[i]) + (float2[i] * (1-scalar))
        }
        return Tensor.fromBlob(resulting, longArrayOf(1, 3, tensor1.shape()[2], tensor1.shape()[3]))
    }

    private fun colorTempInterpolate(iT: Tensor, iS: Tensor): Tensor {
        val colorTemperatures = mapOf('T' to 2850, 'F' to 3800, 'D' to 5500, 'C' to 6500, 'S' to 7500)
        val cct1 = colorTemperatures['T']!!.toDouble()
        val cct2 = colorTemperatures['S']!!.toDouble()

        // Interpolation weight
        val cct1inv = 1.0 / cct1
        val cct2inv = 1.0 / cct2
        val tempinvD = 1.0 / colorTemperatures['D']!!.toDouble()

        val gD = (tempinvD - cct2inv) / (cct1inv - cct2inv)

        val iD = multiplyAndAddTensors(iT, iS, gD.toFloat())
        Log.i("ID IT IS", "${iD.shape().toList()} ${iT.shape().toList()} ${iS.shape().toList()}")
        return iD
    }

    private fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int) : Bitmap {
        // Create empty bitmap in ARGB format
        val bmp: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height * 3)

        // mapping smallest value to 0 and largest value to 255
        val maxValue = floatArray.max()
        val minValue = floatArray.min()
        val delta = maxValue-minValue

        // Define if float min..max will be mapped to 0..255 or 255..0
        val conversion = { v: Float -> ((v-minValue)/delta*255.0f).roundToInt()}

        // copy each value from float array to RGB channels
        for (i in 0 until width * height) {
            val r = conversion(floatArray[i])
            val g = conversion(floatArray[i+width*height])
            val b = conversion(floatArray[i+2*width*height])
            pixels[i] = rgb(r, g, b) // you might need to import for rgb()
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        return bmp
    }

    fun whiteBalance(rgbBitmap: Bitmap): Bitmap {
        val rgbTensor = TensorImageUtils.bitmapToFloat32Tensor(rgbBitmap, mean, std)

        Log.i("imageShape", "${rgbTensor.shape().toList()}")

        val outputs = deepWB(rgbTensor)
        Log.i("White Balancing", "Output Shape: ${outputs.shape().toList()}")

        return floatArrayToBitmap(outputs.dataAsFloatArray, outputs.shape()[3].toInt(), outputs.shape()[2].toInt())
    }
}