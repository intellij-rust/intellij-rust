package org.rust.cargo.project.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.module.persistence.CargoModuleService
import org.rust.cargo.util.getService
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFile
import java.io.File

object RustCrateUtil

fun Module.getSourceRoots(includingTestRoots: Boolean = false): Collection<VirtualFile> =
    ModuleRootManager.getInstance(this).getSourceRoots(includingTestRoots).toList()

val Module.crateRoots: Sequence<RustModItem>
    get() = crateRootFiles.asSequence()
        .mapNotNull { PsiManager.getInstance(project).findFile(it) as? RustFile }
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

val Module.crateRootFiles: Collection<VirtualFile>
    get() = targets.mapNotNull { target ->
        contentRoot.findFileByRelativePath(target.path)
    }

val Module.targets: Collection<CargoProjectDescription.Target> get() =
    getService<CargoModuleService>().targets

data class ExternCrate(
    /**
     * Name of a crate as appears in `extern crate foo;`
     */
    val name: String,

    /**
     * Root module file (typically `src/lib.rs`)
     */
    val psiFile: Lazy<PsiFile?>
)

/**
 * A set of external crates for the module. External crate can refer
 * to another module or a library.
 */
val Module.externCrates: Collection<ExternCrate> get() =
    getService<CargoModuleService>().externCrates.mapNotNull { crate ->
        val file = File(crate.path)
        val vFile = if (file.isAbsolute)
            LocalFileSystem.getInstance().findFileByIoFile(file)
        else
            contentRoot.findFileByRelativePath(crate.path)

        vFile?.let { vFile ->
            ExternCrate(
                crate.name,
                lazy {
                    PsiManager.getInstance(project).findFile(vFile)
                }
            )
        }
    }

private val Module.contentRoot: VirtualFile get() =
    ModuleRootManager.getInstance(this).contentRoots.single()
