package com.manjaniq.core.interfaces

import com.manjaniq.core.models.EngineState

interface EngineStatusProvider {
    fun getCurrentState(): EngineState
}