package com.fraudguard.demo;

import java.util.Arrays;

/**
 * Simple StandardScaler: x' = (x - mean) / scale
 */
public class StandardScaler {
	private final float[] mean;
	private final float[] scale;

	public StandardScaler(float[] mean, float[] scale) {
		this.mean = mean;
		this.scale = scale;
	}

	public float[] transform(float[] input) {
		float[] out = Arrays.copyOf(input, input.length);
		for (int i = 0; i < out.length; i++) {
			float m = (i < mean.length) ? mean[i] : 0f;
			float s = (i < scale.length && scale[i] != 0f) ? scale[i] : 1f;
			out[i] = (out[i] - m) / s;
		}
		return out;
	}
}

