package com.aghielaan22.label

import androidx.media3.common.PlaybackException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns a technical error into a sentence a person can act on. */
fun friendlyMessage(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.toList()
    return when {
        chain.any {
            it is UnknownHostException || it is ConnectException || it is SocketTimeoutException
        } -> "Can't reach YouTube. Check your internet connection and try again."

        chain.any { it is ReCaptchaException } ->
            "YouTube is limiting requests right now. Wait a minute and try again."

        chain.any { it is IOException } ->
            "The connection was interrupted. Check your internet and try again."

        else -> "Something went wrong. Please try again."
    }
}

fun friendlyPlaybackMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Lost the connection. Check your internet and try again."

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "YouTube refused this song's stream. Try again in a moment."

    else -> "This song couldn't be played."
}