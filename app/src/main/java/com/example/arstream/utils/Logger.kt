// File: ARStreamApp/app/src/main/java/com/example/arstream/utils/Logger.kt
package com.example.arstream.utils

import timber.log.Timber

/**
 * Logging utility wrapper around Timber for consistent logging
 */
object Logger {
    fun v(tag: String, message: String) {
        Timber.tag(tag).v(message)
    }

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
    }

    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).wtf(throwable, message)
    }
}