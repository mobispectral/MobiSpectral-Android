package com.shahzaib.mobispectral

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.nio.FloatBuffer

class Reconstruction(context: Context, modelPath: String) {
    private var model: Module? = null
    private var bitmapsWidth = Utils.torchWidth
    private var bitmapsHeight = Utils.torchHeight

    init {
        Log.i("Reconstruction Model Load", Utils.assetFilePath(context, modelPath).toString())
        model = Module.load(Utils.assetFilePath(context, modelPath))
    }

    private fun getNormalizedTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height

        val pixelCount = width*height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var min = 1000
        var max = 0

        for (i in 0 until pixelCount) {
            val color = pixels[i]
            val red: Int = color shr 16 and 0xFF
            val green: Int = color shr 8 and 0xFF
            val blue: Int = color and 0xFF

            if (red > max)
                max = red
            else if (green > max)
                max = green
            else if (blue > max)
                max = blue

            if (red < min)
                min = red
            else if (green < min)
                min = green
            else if (blue < min)
                min = blue
        }
        val diff = (max - min).toFloat()
        val outBuffer: FloatBuffer = Tensor.allocateFloatBuffer(3 * width * height)

        for (i in 0 until pixelCount) {
            val color = pixels[i]
            val red = ((color shr 16 and 0xFF) - min).toFloat() / diff
            val green = ((color shr 8 and 0xFF) - min).toFloat() / diff
            val blue = ((color and 0xFF) - min).toFloat() / diff

            outBuffer.put(i, red)
            outBuffer.put(pixelCount + i, green)
            outBuffer.put(pixelCount * 2 + i, blue)
        }

        val firstPixel: Triple<Int, Int, Int> = Triple(pixels[0] shr 16 and 0xFF, pixels[0] shr 8 and 0xFF, pixels[0] and 0xFF)
        Log.i("Normalization", "Min: $min, Max: $max, Delta: $diff")
        Log.i("Normalization", "First Pixel: ${firstPixel.first} ${firstPixel.second} ${firstPixel.third}")
        Log.i("Normalization", "First Pixel Normalized: [${outBuffer.get(0)}, ${outBuffer.get(pixelCount)}, ${outBuffer.get(pixelCount*2)}]")
        return Tensor.fromBlob(outBuffer, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    @Suppress("SameParameterValue")
    private fun getOneBand(tensor: Tensor, offset: Int): Tensor {
        val tensorDoubleArray = tensor.dataAsFloatArray
        val floatArray = FloatArray((bitmapsHeight*bitmapsWidth))
        val bandOffset = bitmapsHeight*bitmapsWidth*offset
        for (i in 0 until (bitmapsHeight*bitmapsWidth)){
            floatArray[i] = tensorDoubleArray[bandOffset+i]
        }
        val size = longArrayOf(1, 1, bitmapsHeight.toLong(), bitmapsWidth.toLong())
        return Tensor.fromBlob(floatArray, size)
    }

    @Suppress("SameParameterValue")
    private fun concatenate(tensor1: Tensor, tensor2: Tensor, channels: Long): Tensor {
        val rgbArray = tensor1.dataAsFloatArray
        val nirArray = tensor2.dataAsFloatArray
        val concatenated = FloatArray(rgbArray.size + nirArray.size)
        System.arraycopy(rgbArray, 0, concatenated, 0, rgbArray.size)
        System.arraycopy(nirArray, 0, concatenated, rgbArray.size, nirArray.size)
        val size = longArrayOf(1, channels, bitmapsHeight.toLong(), bitmapsWidth.toLong())
        return Tensor.fromBlob(concatenated, size)
    }

    fun predict(rgbBitmap: Bitmap, nirBitmap: Bitmap): FloatArray {
        bitmapsWidth = rgbBitmap.width
        bitmapsHeight = rgbBitmap.height

        val rgbBitmapTensor = getNormalizedTensor(rgbBitmap)
        val nirTensor: Tensor = getOneBand(getNormalizedTensor(nirBitmap), 0)

        val imageTensor: Tensor = concatenate(rgbBitmapTensor, nirTensor, 4)
        val inputs: IValue = IValue.from(imageTensor)

        val outputs: Tensor = model?.forward(inputs)?.toTensor()!!
        Log.i("Reconstruction Tensors", "RGB ${rgbBitmapTensor.shape().toList()} + NIR ${nirTensor.shape().toList()} = Concat ${imageTensor.shape().toList()} -> [Reconstruction] -> ${outputs.shape().toList()}")

        //saveHypercube("Output.txt", outputs.dataAsFloatArray, Utils.hypercubeDirectory)
        return outputs.dataAsFloatArray
    }
}