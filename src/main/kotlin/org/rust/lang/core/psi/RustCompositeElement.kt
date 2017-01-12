package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.cargoProject
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.ref.RustReference

interface RustCompositeElement : PsiElement {
    override fun getReference(): RustReference?
}

val RustCompositeElement.containingMod: RustMod?
    get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RustMod::class.java)

val RsModDeclItem.containingMod: RustMod
    get() = (this as RustCompositeElement).containingMod
        ?: error("Rust mod decl outside of a module")

val RustCompositeElement.crateRoot: RustMod? get() {
    val mod = containingFile as? RustFile ?: return null

    val root = mod.superMods.lastOrNull()
    return if (root != null && root.isCrateRoot)
        root
    else
        null
}

val RustCompositeElement.containingCargoTarget: CargoProjectDescription.Target? get() {
    val cargoProject = module?.cargoProject ?: return null
    val crateRoot = crateRoot ?: return null
    return cargoProject.findTargetForCrateRootFile(crateRoot.containingFile.virtualFile)
}
