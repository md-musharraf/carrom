package com.example.royalcarromclassic.core.logging

import android.util.Log

/**
 * Enterprise-grade structured logging utility.
 * Safe for release builds with zero runtime overhead when debug logging is disabled.
 */
object AppLogger {
    private const val DEFAULT_TAG = "RoyalCarrom"
    var isDebugEnabled: Boolean = true

    fun d(tag: String = DEFAULT_TAG, message: () -> String) {
        if (isDebugEnabled) {
            Log.d(tag, message())
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: () -> String) {
        Log.i(tag, message())
    }

    fun w(tag: String = DEFAULT_TAG, message: () -> String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message(), throwable)
        } else {
            Log.w(tag, message())
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: () -> String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message(), throwable)
        } else {
            Log.e(tag, message())
        }
    }
}
