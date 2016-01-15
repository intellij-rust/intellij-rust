package org.rust.cargo.project.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.module.util.getSourceRoots

object ProjectUtil

fun Project.getCrateSourceRootFor(file: VirtualFile): VirtualFile? =
    ModuleUtilCore.findModuleForFile(file, this)?.let {
        it.getSourceRoots().find { root ->
            FileUtil.isAncestor(root.canonicalPath!!, file.canonicalPath!!, /* strict = */ false)
        }
    }

fun Project.getModules(): Collection<Module> =
    ModuleManager.getInstance(this).modules.toList()
