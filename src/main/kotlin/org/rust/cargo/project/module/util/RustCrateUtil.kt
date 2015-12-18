package org.rust.cargo.project.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.rust.cargo.project.module.RustExecutableModuleType
import org.rust.cargo.project.module.RustLibraryModuleType
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.RustModules

object RustCrateUtil

fun Module.getSourceRoots(includingTestRoots: Boolean = false): Collection<VirtualFile> =
    ModuleRootManager.getInstance(this).getSourceRoots(includingTestRoots).toList()

val Module.rootMod: RustModItem?
    get() = getSourceRoots(includingTestRoots = false).firstOrNull()?.let {
        var target = when (getOptionValue(Module.ELEMENT_TYPE)) {
            RustExecutableModuleType.MODULE_TYPE_ID -> RustModules.MAIN_RS
            RustLibraryModuleType.MODULE_TYPE_ID    -> RustModules.LIB_RS
            else                                    -> return null
        }

        it.findChild(target)?.let {
            PsiManager.getInstance(project).findFile(it)?.let {
                when (it) {
                    is RustFileImpl -> it.mod
                    else            -> null
                }
            }
        }
    }

fun Module.relativise(f: VirtualFile): String? =
    getSourceRoots()
        .find {
            FileUtil.isAncestor(it.path, f.path, /* strict = */ false)
        }
        ?.let {
            FileUtil.getRelativePath(it.canonicalPath!!, f.canonicalPath!!, '/')
        }
