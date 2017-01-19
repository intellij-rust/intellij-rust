package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.ref.RsReference

interface RsCompositeElement : PsiElement {
    override fun getReference(): RsReference?
}

val RsCompositeElement.containingMod: RsMod?
    get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RsMod::class.java)

val RsModDeclItem.containingMod: RsMod
    get() = (this as RsCompositeElement).containingMod
        ?: error("Rust mod decl outside of a module")

val RsCompositeElement.crateRoot: RsMod? get() {
    val mod = containingFile as? RsFile ?: return null

    val root = mod.superMods.lastOrNull()
    return if (root != null && root.isCrateRoot)
        root
    else
        null
}

val RsCompositeElement.containingCargoTarget: CargoWorkspace.Target? get() {
    val cargoProject = module?.cargoWorkspace ?: return null
    val crateRoot = crateRoot ?: return null
    return cargoProject.findTargetForCrateRootFile(crateRoot.containingFile.virtualFile)
}
