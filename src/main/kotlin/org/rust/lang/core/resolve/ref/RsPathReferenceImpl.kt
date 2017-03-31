package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.asRustPath
import org.rust.lang.core.psi.ext.isStarImport
import org.rust.lang.core.resolve.*


class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsReference {

    override val RsPath.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RsCompositeElement> =
        MultiResolveProcessor(element.referenceName).run { processResolveVariants(element, false, it) }

    override fun getVariants(): Array<out Any> =
        CompletionProcessor().run { processResolveVariants(element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    private val namespaceForResolve: Namespace? get() {
        val parent = element.parent
        return when (parent) {
            is RsPath, is RsTypeReference -> Namespace.Types
            is RsUseItem -> if (parent.isStarImport) Namespace.Types else null
            is RsPathExpr -> Namespace.Values
            else -> null
        }
    }

    private val namespaceForCompletion: Namespace?
        get() = if (element.parent is RsPathExpr) null else namespaceForResolve
}
