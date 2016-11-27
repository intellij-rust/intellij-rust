package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.RustLint
import org.rust.ide.inspections.RustLintLevel
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference

interface RustCompositeElement : PsiElement {
    override fun getReference(): RustReference?
}

val RustCompositeElement.containingMod: RustMod?
    get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RustMod::class.java)

/**
 * Returns the level of the given lint for this element.
 */
fun PsiElement.lintLevel(lint: RustLint): RustLintLevel {
    var level: RustLintLevel? = null
    if (this is RustDocAndAttributeOwner) {
        level = queryAttributes.metaItems
            .filter { it.metaItemList.any { it.text == lint.id } }
            .map { RustLintLevel.valueForId(it.identifier.text) }
            .filterNotNull()
            .firstOrNull()
    }
    if (level == null) {
        level = parentOfType<RustDocAndAttributeOwner>()?.lintLevel(lint)
    }
    return level ?: lint.defaultLevel
}
