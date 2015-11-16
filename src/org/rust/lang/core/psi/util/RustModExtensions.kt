package org.rust.lang.core.psi.util

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
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

private val RustModItem.ownsDirectory: Boolean
    get() = containingMod != null || // any inline nested module owns a directory
        containingFile.name == MOD_RS ||
        isCrateRoot


sealed class ChildModFile {
    val mod: RustModItem?
        get() = (this as? Found)?.file?.firstChild as? RustModItem

    class NotFound : ChildModFile()
    class Found(val file: PsiFile) : ChildModFile()
    class Ambigious(val paths: Collection<PsiFile>) : ChildModFile()
}

fun RustModItem.submoduleFile(modName: String): ChildModFile {
    if (!ownsDirectory) {
        return ChildModFile.NotFound()
    }

    val dir = modDir
    val dirMod = dir?.findSubdirectory(modName)?.findFile(MOD_RS)
    val fileMod = dir?.findFile("$modName.rs")

    val variants = listOf(fileMod, dirMod).filterNotNull()

    when (variants.size) {
        0 -> return ChildModFile.NotFound()
        2 -> return ChildModFile.Ambigious(variants)
    }

    return ChildModFile.Found(variants.first())
}


