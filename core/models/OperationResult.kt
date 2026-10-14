package com.manjaniq.core.models

sealed class OperationResult<T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure<T>(val errorMessage: String, val errorCode: Int? = null) : OperationResult<T>()
}