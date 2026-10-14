package com.manjaniq.core.interfaces

interface Logger {
    fun log(level: String, message: String)
    fun debug(message: String) = log("DEBUG", message)
    fun info(message: String) = log("INFO", message)
    fun warn(message: String) = log("WARN", message)
    fun error(message: String) = log("ERROR", message)
}