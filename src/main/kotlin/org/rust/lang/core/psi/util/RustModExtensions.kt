package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.util.getCrateSourceRootFor
import org.rust.lang.core.resolve.indexes.RustModulesIndex
import org.rust.lang.core.names.RustAnonymousId
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.RustUseItem


object RustModules {
    val LIB_RS  = "lib.rs"
    val MAIN_RS = "main.rs"
    val MOD_RS  = "mod.rs"
}

/**
 * Seals down canonical-path inside the module-tree of the particular
 * crate
 *
 * NOTE: That this is unique (since its a _tree_) for any particular module
 */
public val RustModItem.canonicalName: RustQualifiedName?
    get() = if (!isCrateRoot)   name?.let { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalName) }
            else RustAnonymousId

/**
 *  Returns a parent module (`super::` in paths).
 *
 *  The parent module may be in the same or other file.
 *
 *  Reference:
 *    https://doc.rust-lang.org/reference.html#paths
 */
public val RustModItem.`super`: RustModItem?
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
        val file = containingFile.virtualFile
        val containingDir = file.parent
        return project.getCrateSourceRootFor(file)?.let {
            it.equals(containingDir) && (containingFile.name == RustModules.MAIN_RS || containingFile.name == RustModules.LIB_RS)
        } ?: false
    }

val RustModItem.modDecls: Collection<RustModDeclItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

val RustModItem.useDeclarations: Collection<RustUseItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustUseItem::class.java)

fun RustModDeclItem.getOrCreateModuleFile(): PsiFile? {
    return  reference!!.resolve()?.let { it.containingFile } ?:
            containingMod?.ownedDirectory?.createFile(name ?: return null)
}
