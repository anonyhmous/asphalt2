package com.asphalt.app.data

import android.util.Log

enum class OperationType(val value: String) {
    CREATE("create"), UPDATE("update"), DELETE("delete"), LIST("list"), GET("get"), WRITE("write"),
}

/**
 * Logs a Firestore failure WITHOUT personal data (the previous version logged the user's e-mail,
 * provider info and uid to logcat). Returns a short description for callers.
 */
fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val summary = "Firestore ${operationType.value} failed at ${path ?: "?"}: ${exception.javaClass.simpleName}"
    Log.e("FirestoreError", summary)
    return summary
}
