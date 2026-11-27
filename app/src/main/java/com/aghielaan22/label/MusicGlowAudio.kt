package com.aghielaan22.label

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow

internal class MusicGlowFeed {
    val level = MutableStateFlow(0f)
}

internal object MusicGlowAudio {
    private val feed = AtomicReference<MusicGlowFeed?>(null)

    fun subscribe(observer: MusicGlowFeed) {
        observer.level.value = 0f
        feed.set(observer)
    }

    fun unsubscribe(observer: MusicGlowFeed) {
        feed.compareAndSet(observer, null)
        observer.level.value = 0f
    }

    fun observer(): MusicGlowFeed? = feed.get()
}

@OptIn(UnstableApi::class)
internal class MusicGlowRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf(MusicGlowProcessor()))
        .build()
}

@OptIn(UnstableApi::class)
private class MusicGlowProcessor : BaseAudioProcessor() {
    private val meter = PcmGlowMeter()
    private var previousObserver: MusicGlowFeed? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount > 0 && inputAudioFormat.sampleRate > 0
        ) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = inputAudioFormat
        if (format.encoding == C.ENCODING_PCM_16BIT && format.channelCount > 0 && format.sampleRate > 0) {
            meter.configure(format.sampleRate, format.channelCount)
        } else {
            meter.reset()
        }
        previousObserver?.level?.value = 0f
    }

    override fun onReset() {
        meter.reset()
        previousObserver?.level?.value = 0f
        previousObserver = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val observer = MusicGlowAudio.observer()
        if (observer !== previousObserver) {
            meter.reset()
            previousObserver = observer
        }
        if (observer != null) {
            val measured = meter.read(inputBuffer)
            if (!measured.isNaN()) observer.level.value = measured
        }
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }
}