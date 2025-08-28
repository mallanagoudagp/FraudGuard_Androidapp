package com.fraudguard.demo;

import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

/**
 * Runs a TFLite Autoencoder model. Input/Output are assumed shape [1, N].
 */
public class TouchAETFLiteScorer implements AutoCloseable {
	private static final String TAG = "FraudGuard";

	private final Interpreter interpreter;
	private final int featureSize;

	public TouchAETFLiteScorer(MappedByteBuffer modelBuffer, int featureSize) {
		this.featureSize = featureSize;
		Interpreter.Options opts = new Interpreter.Options();
		this.interpreter = new Interpreter(modelBuffer, opts);
	}

	public static class ScoreResult {
		public final float mse;
		public final float threshold;
		public final float score; // normalized [0,1]

		public ScoreResult(float mse, float threshold) {
			this.mse = mse;
			this.threshold = threshold;
			float s = threshold <= 0 ? 0f : (mse / threshold);
			if (s < 0f) s = 0f;
			if (s > 1f) s = 1f;
			this.score = s;
		}
	}

	public ScoreResult score(float[] input, float threshold) {
		if (input.length != featureSize) {
			Log.w(TAG, "Unexpected feature size " + input.length + ", expected " + featureSize);
		}
		// Build input tensor [1, N]
		float[][] in = new float[1][featureSize];
		System.arraycopy(input, 0, in[0], 0, Math.min(input.length, featureSize));

		// Prepare output buffer [1, N]
		float[][] out = new float[1][featureSize];
		try {
			interpreter.run(in, out);
		} catch (Throwable t) {
			Log.e(TAG, "TFLite run failed: " + t.getMessage(), t);
			return new ScoreResult(0f, threshold);
		}

		// MSE
		float mse = 0f;
		int n = featureSize;
		for (int i = 0; i < n; i++) {
			float d = in[0][i] - out[0][i];
			mse += d * d;
		}
		mse /= n;
		return new ScoreResult(mse, threshold);
	}

	@Override
	public void close() {
		interpreter.close();
	}
}

