package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.RsMacroInvocation
import org.rust.lang.core.psi.RsMacroItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.stubs.index.RsMacroDefinitionIndex
import org.rust.lang.core.types.BoundElement

class RsMacroReferenceImpl(macroInvocation: RsMacroInvocation) : RsReferenceBase<RsMacroInvocation>(macroInvocation) {
    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        StubIndex.getElements(
            RsMacroDefinitionIndex.KEY,
            element.referenceName,
            element.project,
            GlobalSearchScope.allScope(element.project),
            RsMacroDefinition::class.java
        ).mapNotNull { it.parentOfType<RsMacroItem>() }
            .map { BoundElement(it) }
            .toList()

    override val RsMacroInvocation.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun getVariants(): Array<Any> = emptyArray()
}
