package com.manjaniq.core.models

enum class AetherState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR;

    fun isRunning() = this == RUNNING
    fun isTransitioning() = this == STARTING || this == STOPPING
}
