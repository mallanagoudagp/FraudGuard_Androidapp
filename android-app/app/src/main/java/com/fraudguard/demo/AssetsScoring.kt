package com.fraudguard.demo

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object AssetsScoring {
	private const val TAG = "FraudGuard"

	fun loadModelFromAssets(context: Context, assetName: String): MappedByteBuffer {
		context.assets.openFd(assetName).use { afd ->
			val startOffset = afd.startOffset
			val declaredLength = afd.length
			java.io.FileInputStream(afd.fileDescriptor).channel.use { fc ->
				return fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
			}
		}
	}

	fun readJsonObject(context: Context, assetName: String): JSONObject {
		context.assets.open(assetName).use { input ->
			BufferedReader(InputStreamReader(input)).use { br ->
				val text = br.readText()
				return JSONObject(text)
			}
		}
	}

	fun parseScaler(json: JSONObject): StandardScaler {
		val meanArr = json.getJSONArray("mean")
		val scaleArr = json.getJSONArray("scale")
		val mean = FloatArray(meanArr.length()) { i -> meanArr.getDouble(i).toFloat() }
		val scale = FloatArray(scaleArr.length()) { i -> scaleArr.getDouble(i).toFloat() }
		return StandardScaler(mean, scale)
	}

	fun scoreOnce(
		context: Context,
		features: FloatArray,
		modelAsset: String = "touch_ae.tflite",
		scalerAsset: String = "scaler.json",
		thresholdAsset: String = "threshold.json"
	): TouchAETFLiteScorer.ScoreResult? {
		return try {
			val scalerJson = readJsonObject(context, scalerAsset)
			val thrJson = readJsonObject(context, thresholdAsset)
			val scaler = parseScaler(scalerJson)
			val threshold = thrJson.optDouble("threshold", 0.05).toFloat()

			val std = scaler.transform(features)
			val model = loadModelFromAssets(context, modelAsset)
			TouchAETFLiteScorer(model, std.size).use { scorer ->
				val res = scorer.score(std, threshold)
				Log.i(TAG, "AE score=${res.score}, mse=${res.mse}, thr=${res.threshold}")
				res
			}
		} catch (t: Throwable) {
			Log.e(TAG, "Scoring failed: ${t.message}", t)
			null
		}
	}
}

