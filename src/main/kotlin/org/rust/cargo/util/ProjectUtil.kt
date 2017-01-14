package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Extracts modules for the given project
 */
val Project.modules: Collection<Module>
    get() = ModuleManager.getInstance(this).modules.toList()

/**
 * Extracts modules with `Cargo.toml` present at the root.
 */
val Project.modulesWithCargoProject: Collection<Module>
    get() = modules.filter { it.cargoProjectRoot != null }

/**
 * Looks up `PsiFile` for the virtual-file supplied inside the given project
 */
fun Project.getPsiFor(file: VirtualFile?): PsiFile? =
    file?.let { PsiManager.getInstance(this).findFile(it) }



