package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

object ProjectUtil

/**
 * Extracts modules for the given project
 */
val Project.modules: Collection<Module>
    get() = ModuleManager.getInstance(this).modules.toList()



