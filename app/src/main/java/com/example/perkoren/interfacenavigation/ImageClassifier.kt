package com.example.perkoren.interfacenavigation

/*
Licensed under the Apache License, Version 2.0 (the "License");
This code is based on https://github.com/tensorflow/tensorflow/blob/master/tensorflow/examples/android/src/org/tensorflow/demo/TensorFlowImageClassifier.java
*/

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.IOException

class ImageClassifier {

    companion object {
        private const val ENABLE_LOG_STATS = false
        private val TAG = "InterfaceNavigation"
        private const val COLOR_CHANNELS = 3
        const val IMAGE_SIZE = 224
        private const val LABELS_FILE_PATH = "output_labels.txt"
        private const val GRAPH_FILE_PATH = "file:///android_asset/output_graph.pb"
        private const val inputName: String = "Placeholder"
        private const val outputName: String = "final_result"
    }

    private var labels: List<String>
    private val imageBitmapPixels: IntArray = IntArray(IMAGE_SIZE * IMAGE_SIZE)
    private val imageNormalizedPixels: FloatArray = FloatArray(IMAGE_SIZE * IMAGE_SIZE * COLOR_CHANNELS)
    private var results: FloatArray
    private var tensorFlowInference: TensorFlowInferenceInterface


    constructor(assetManager: AssetManager) {
        labels = loadFile(assetManager, LABELS_FILE_PATH)
        results = FloatArray(labels.size)
        tensorFlowInference = TensorFlowInferenceInterface(assetManager, GRAPH_FILE_PATH)
    }

    @Throws(IOException::class)
    private fun loadFile(assetManager: AssetManager, fileName: String): List<String> {
        return assetManager.open(fileName).bufferedReader().useLines { it.toList() }
    }

    fun recognizeImage(bitmap: Bitmap): Output {
        preprocessImageToNormalizedFloats(bitmap)
        classifyAndFetch()
       val ind = results.indices.maxBy { results[it] } ?: -1
       return Output(labels[ind],results.max()?:0f)
    }

    private fun preprocessImageToNormalizedFloats(bitmap: Bitmap) {
        val imageMean = 128
        val imageStd = 128.0f
        bitmap.getPixels(imageBitmapPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in imageBitmapPixels.indices) {
            val imgBitmpPix = imageBitmapPixels[i]
            imageNormalizedPixels[i * 3] = ((imgBitmpPix shr 16 and 0xFF) - imageMean) / imageStd
            imageNormalizedPixels[i * 3 + 1] = ((imgBitmpPix shr 8 and 0xFF) - imageMean) / imageStd
            imageNormalizedPixels[i * 3 + 2] = ((imgBitmpPix and 0xFF) - imageMean) / imageStd
        }
    }

    private fun classifyAndFetch() {
        try {
            tensorFlowInference.feed(inputName, imageNormalizedPixels, 1L, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong(), COLOR_CHANNELS.toLong())
            tensorFlowInference.run(arrayOf(outputName), ENABLE_LOG_STATS)
            tensorFlowInference.fetch(outputName, results)
        } catch(e:Exception){
            Log.e(TAG,e.message)
        }
    }

}