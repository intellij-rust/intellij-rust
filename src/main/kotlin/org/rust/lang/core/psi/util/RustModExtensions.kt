package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.module.util.isCrateRootFile
import org.rust.lang.core.names.RustAnonymousId
import org.rust.lang.core.names.RustFileModuleId
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.modulePath
import org.rust.lang.core.resolve.indexes.RustModulesIndex


object RustModules {
    val MOD_RS  = "mod.rs"
}

/**
 * Seals down canonical-path inside the module-tree of the particular
 * crate
 *
 * NOTE: That this is unique (since its a _tree_) for any particular module
 */
val RustModItem.canonicalName: RustQualifiedName?
    get() =
        when (isCrateRoot) {
            true -> RustAnonymousId
            else -> name?.let { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalName) }
        }


val RustModItem.canonicalNameInFile: RustQualifiedName?
    get() =
        when (containingMod) {
            null -> containingFile.modulePath?.let  { RustFileModuleId(it) }
            else -> name?.let                       { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalName) }
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

        val parent  = containingMod ?: return containingFile.originalFile.parent
        val name    = this.name     ?: return null

        return parent.ownedDirectory?.findSubdirectory(name)
    }

val RustModItem.isCrateRoot: Boolean
    get() {
        val vFile = containingFile.originalFile.virtualFile
        val module = getModule()
        if (containingMod != null || vFile == null || module == null) {
            return false
        }
        return module.isCrateRootFile(vFile)
    }

val RustModItem.modDecls: Collection<RustModDeclItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

val RustModItem.isTopLevelInFile: Boolean
    get() = this.containingMod == null
