// Package com.manjaniq.core.models provides utility data models for the application.
package com.manjaniq.core.models

// Result represents the result of an operation, similar to Kotlin's Result
// but more lightweight for our use case.
data class Result<T>(
    val isSuccess: Boolean,
    val data: T? = null,
    val errorMessage: String? = null,
    val errorCode: Int? = null
) {
    companion object {
        // Create a successful result
        fun <T> success(data: T): Result<T> {
            return Result(true, data = data)
        }

        // Create a failure result
        fun <T> failure(errorMessage: String, errorCode: Int? = null): Result<T> {
            return Result(false, errorMessage = errorMessage, errorCode = errorCode)
        }
    }

    // Convenience accessors. Note: `isSuccess` is a val property, so Kotlin
    // already exposes boolean `isSuccess()` on the JVM; declaring an
    // explicit fun isSuccess() would be a platform-signature clash and
    // break compilation.
    fun getOrNull(): T? {
        return if (isSuccess) data else null
    }

    fun getErrorOrNull(): String? {
        return if (!isSuccess) errorMessage else null
    }
}
