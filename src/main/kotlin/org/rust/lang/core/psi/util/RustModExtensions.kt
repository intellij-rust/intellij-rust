package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import org.rust.cargo.project.module.util.isCrateRootFile
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.resolve.indexes.RustModulesIndex


object RustModules {
    val MOD_RS  = "mod.rs"
}

/**
 *  Returns a parent module (`super::` in paths).
 *
 *  The parent module may be in the same or other file.
 *
 *  Reference:
 *    https://doc.rust-lang.org/reference.html#paths
 */
val RustModItem.`super`: RustModItem?
    get() {
        if (isCrateRoot) return null

        val self = this
        val superInFile = self.containingMod
        if (superInFile != null)
            return superInFile

        return RustModulesIndex.getSuperFor(self)
    }

internal val RustModItem.ownsDirectory: Boolean
    get() = containingMod != null /* Any inline nested module owns a directory */
        ||  containingFile.name == RustModules.MOD_RS
        ||  isCrateRoot

internal val RustModItem.ownedDirectory: PsiDirectory?
    get() {
        if (!ownsDirectory) return null

        val parent  = containingMod ?: return containingFile.parent
        val name    = this.name     ?: return null

        return parent.ownedDirectory?.findSubdirectory(name)
    }

private val RustModItem.isCrateRoot: Boolean
    get() {
        val vFile = containingFile.virtualFile
        val module = getModule()
        if (containingMod != null || vFile == null || module == null) {
            return false
        }
        return module.isCrateRootFile(vFile)
    }

