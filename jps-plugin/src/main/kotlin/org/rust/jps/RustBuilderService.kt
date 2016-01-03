package org.rust.jps

import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.ModuleLevelBuilder

import java.util.Collections

class RustBuilderService : BuilderService() {
    override fun createModuleLevelBuilders(): List<ModuleLevelBuilder> = listOf(RustBuilder())
}
