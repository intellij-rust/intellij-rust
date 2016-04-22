package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

object ProjectUtil

/**
 * Extracts modules for the given project
 */
fun Project.getModules(): Collection<Module> =
    ModuleManager.getInstance(this).modules.toList()



