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

        val name = this.name ?: return null

        return parent.modDir?.findSubdirectory(name)
    }


private val RustModItem.isCrateRoot: Boolean
    get() {
        val containingDirPath = Paths.get(containingFile.containingDirectory.virtualFile.canonicalPath)
        return project.getSourceRootFor(containingDirPath)?.let {
            it.equals(containingDirPath) && (containingFile.name == RustModules.MAIN_RS || containingFile.name == RustModules.LIB_RS)
        } ?: false
    }

internal val RustModItem.ownsDirectory: Boolean
    get() =     containingMod != null /* Any inline nested module owns a directory */
            ||  containingFile.name == RustModules.MOD_RS
            ||  isCrateRoot

val RustModItem.modDecls: Collection<RustModDeclItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

val RustModItem.useDeclarations: Collection<RustUseItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustUseItem::class.java)


sealed class ChildModFile {
    val mod: RustModItem?
        get() = when (this) {
            is Found    -> (file as? RustFileImpl)?.mod
            else        -> null
        }

    class NotFound(val suggestedName: String? = null) : ChildModFile()
    class Found(val file: PsiFile) : ChildModFile()
    class Ambiguous(val paths: Collection<PsiFile>) : ChildModFile()
}

/**
 * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
 *
 *  ```
 *  // foo.rs
 *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir
 *
 *  pub mod nested {
 *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs`
 *  }
 *
 *  ```
 *
 *  | A module without a body is loaded from an external file, by default with the same name as the module,
 *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
 *  | from a subdirectory path that mirrors the module hierarchy.
 *
 * Reference:
 *      https://github.com/rust-lang/rust/blob/master/src/doc/reference.md#modules
 */
val RustModDeclItem.moduleFile: ChildModFile
    get() {
        val parent  = containingMod
        val name    = name

        if (parent == null || name == null) {
            return ChildModFile.NotFound()
        }

        val dir     = parent.modDir
        val dirMod  = dir?.findSubdirectory(name)?.findFile(RustModules.MOD_RS)

        val fileName = "$name.rs"
        val fileMod  = dir?.findFile(fileName)

        val variants = listOf(fileMod, dirMod).filterNotNull()

        return when (variants.size) {
            0    -> ChildModFile.NotFound(fileName)
            1    -> ChildModFile.Found(variants.single())
            else -> ChildModFile.Ambiguous(variants)
        }
    }

fun RustModDeclItem.createModuleFile(): PsiFile? {
    val child = moduleFile as? ChildModFile.NotFound ?: return null
    return containingMod?.modDir?.createFile(child.suggestedName ?: return null)
}
