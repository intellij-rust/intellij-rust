package org.rust.cargo.project.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.RustModules

object RustCrateUtil

fun Module.getSourceRoots(includingTestRoots: Boolean = false): Collection<VirtualFile> =
    ModuleRootManager.getInstance(this).getSourceRoots(includingTestRoots).toList()

val Module.crateRoots: Sequence<RustModItem>
    get() = crateRootFiles.asSequence()
        .mapNotNull { PsiManager.getInstance(project).findFile(it) as? RustFileImpl }
        .mapNotNull { it.mod }

fun Module.isCrateRootFile(file: VirtualFile): Boolean =
    crateRootFiles.contains(file)

fun Module.relativise(f: VirtualFile): String? =
    getSourceRoots()
        .find {
            FileUtil.isAncestor(it.path, f.path, /* strict = */ false)
        }
        ?.let {
            FileUtil.getRelativePath(it.canonicalPath!!, f.canonicalPath!!, '/')
        }

private val Module.crateRootFiles: Collection<VirtualFile>
    get() = getSourceRoots(includingTestRoots = false).firstOrNull()?.let { sourceRoot ->
        // TODO: precise information about targets is available via metadata
        val targets = listOf(RustModules.MAIN_RS, RustModules.LIB_RS)
        targets.mapNotNull { sourceRoot.findChild(it) }
    }.orEmpty()

