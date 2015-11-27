package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem


val MOD_RS = "mod.rs"

private val RustModItem.modDir: PsiDirectory?
    get() {
        val parent = containingMod ?: return containingFile.parent

        val name = this.name ?: return null

        return parent.modDir?.findSubdirectory(name)
    }


private val RustModItem.isCrateRoot: Boolean
    get() = containingMod == null &&
        (containingFile.name == "main.rs" || containingFile.name == "lib.rs")

val RustModItem.ownsDirectory: Boolean
    get() = containingMod != null || // any inline nested module owns a directory
        containingFile.name == MOD_RS ||
        isCrateRoot

val RustModItem.modDecls: Collection<RustModDeclItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

sealed class ChildModFile {
    val mod: RustModItem?
        get() = (this as? Found)?.file?.firstChild as? RustModItem

    class NotFound(val suggestedName: String? = null) : ChildModFile()
    class Found(val file: PsiFile) : ChildModFile()
    class Ambiguous(val paths: Collection<PsiFile>) : ChildModFile()
}

val RustModDeclItem.moduleFile: ChildModFile
    get() {
        val parent = containingMod
        val name = name
        if (parent == null || name == null || !parent.ownsDirectory) {
            return ChildModFile.NotFound()
        }

        val dir = parent.modDir
        val dirMod = dir?.findSubdirectory(name)?.findFile(MOD_RS)
        val fileName = "$name.rs"
        val fileMod = dir?.findFile(fileName)

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
