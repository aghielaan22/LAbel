package com.aghielaan22.label;

import java.nio.ByteBuffer;

/** Measures PCM16 without moving or changing the audio buffer. */
final class PcmGlowMeter {
    private int frameBytes;
    private int channels;
    private int windowFrames;
    private double highPassCoefficient;
    private double lowPassCoefficient;
    private double leftDc;
    private double rightDc;
    private double leftBass;
    private double rightBass;
    private int frames;
    private double fullEnergy;
    private double bassEnergy;
    private float envelope;

    void configure(int sampleRate, int channelCount) {
        frameBytes = channelCount * Short.BYTES;
        channels = Math.min(channelCount, 2);
        windowFrames = Math.max(1, sampleRate / 25);
        highPassCoefficient = 1 - Math.exp(-2 * Math.PI * 35 / sampleRate);
        lowPassCoefficient = 1 - Math.exp(-2 * Math.PI * 170 / sampleRate);
        reset();
    }

    void reset() {
        leftDc = rightDc = leftBass = rightBass = 0;
        fullEnergy = bassEnergy = 0;
        frames = 0;
        envelope = 0f;
    }

    /** Returns NaN until a new 40 ms window is ready. */
    float read(ByteBuffer pcm) {
        if (frameBytes <= 0) return Float.NaN;
        float latest = Float.NaN;
        int finalFrameStart = pcm.limit() - frameBytes;
        for (int offset = pcm.position(); offset <= finalFrameStart; offset += frameBytes) {
            double left = pcm.getShort(offset) / 32768.0;
            leftDc += highPassCoefficient * (left - leftDc);
            leftBass += lowPassCoefficient * (left - leftDc - leftBass);
            fullEnergy += left * left;
            bassEnergy += leftBass * leftBass;

            if (channels == 2) {
                double right = pcm.getShort(offset + Short.BYTES) / 32768.0;
                rightDc += highPassCoefficient * (right - rightDc);
                rightBass += lowPassCoefficient * (right - rightDc - rightBass);
                fullEnergy += right * right;
                bassEnergy += rightBass * rightBass;
            }

            if (++frames == windowFrames) {
                double samples = (double) frames * channels;
                double bass = Math.sqrt(bassEnergy / samples);
                double full = Math.sqrt(fullEnergy / samples);
                float target = (float) Math.max(0, Math.min(1, 3.8 * bass + 0.48 * full - 0.035));
                float smoothing = target > envelope ? 0.56f : 0.22f;
                envelope += smoothing * (target - envelope);
                if (envelope < 0.005f) envelope = 0f;
                latest = envelope;
                frames = 0;
                fullEnergy = bassEnergy = 0;
            }
        }
        return latest;
    }
}