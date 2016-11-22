package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustPathExprElement
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.RustResolveEngine


class RustPathReferenceImpl(
    element: RustPathElement
) : RustReferenceBase<RustPathElement>(element),
    RustReference {

    override val RustPathElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RustCompositeElement> {
        val path = element.asRustPath ?: return emptyList()
        return RustResolveEngine.resolve(path, element, namespace)
    }

    override fun resolve(): RustCompositeElement? = ResolveCache.getInstance(element.project)
        .resolveWithCaching(this,
            ResolveCache.AbstractResolver<RustPathReferenceImpl, RustCompositeElement>
            { r, incomplete -> r.resolveInner().firstOrNull() },
            /* needToPreventRecursion = */ true,
            /* incompleteCode = */ false)

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.completePath(element, namespace)

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    private val namespace: Namespace?
        get() = when (element.parent) {
            is RustPathElement, is RustTypeElement -> Namespace.Types
            is RustPathExprElement -> Namespace.Values
            else -> null
        }
}
