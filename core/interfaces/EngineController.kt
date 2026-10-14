package com.manjaniq.core.interfaces

import com.manjaniq.core.models.OperationResult
import com.manjaniq.core.models.RSTAConfig

interface EngineController {
    fun start(config: RSTAConfig): OperationResult<Unit>
    fun stop(): OperationResult<Unit>
}