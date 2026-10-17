// Package com.manjaniq.core.interfaces provides interfaces for the RSTA engine integration.
package com.manjaniq.core.interfaces

import com.manjaniq.core.models.OperationResult
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.core.models.EngineState

interface EngineController {
    fun start(config: RSTAConfig): OperationResult<Unit>
    fun stop(): OperationResult<Unit>
    fun getState(): EngineState
    fun isRunning(): Boolean
    fun getMetrics(): OperationResult<String>
    fun cleanup()
}
