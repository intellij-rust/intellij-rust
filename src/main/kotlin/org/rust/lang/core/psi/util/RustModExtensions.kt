package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.util.getSourceRootFor
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.RustUseItem
import org.rust.lang.core.psi.impl.RustFileImpl
import java.nio.file.Paths


object RustModules {
    val LIB_RS  = "lib.rs"
    val MAIN_RS = "main.rs"
    val MOD_RS  = "mod.rs"
}

private val RustModItem.modDir: PsiDirectory?
    get() {
        val parent = containingMod ?: return containingFile.parent

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
        val self = this
        val superInFile = self.containingMod
        if (superInFile != null) {
            return superInFile
        }

        // TODO(kudinkin, matklad): Fix this
        val dir = self.containingFile?.containingDirectory ?: return null
        val dirOfParent = if (self.ownsDirectory) dir.parent else dir
        dirOfParent?.files.orEmpty()
            .filterIsInstance<RustFileImpl>()
            .map { it.mod }
            .filterNotNull()
            .forEach { mod ->
                for (declaration in mod.modDecls) {
                    if (declaration.reference?.resolve() == self) {
                        return mod
                    }
                }
            }

        return null
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
        val containingDirPath = Paths.get(containingFile.containingDirectory.virtualFile.canonicalPath)
        return project.getSourceRootFor(containingDirPath)?.let {
            it.equals(containingDirPath) && (containingFile.name == RustModules.MAIN_RS || containingFile.name == RustModules.LIB_RS)
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
