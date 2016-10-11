package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.RustResolveEngine


class RustPathReferenceImpl(
    element: RustPathElement
) : RustReferenceBase<RustPathElement>(element),
    RustReference {

    override val RustPathElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RustNamedElement> {
        val path = element.asRustPath ?: return emptyList()
        return RustResolveEngine.resolve(path, element, namespace)
    }

    override fun resolve(): RustNamedElement? = multiResolve().firstOrNull()

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
            is RustPatStructElement -> Namespace.Types
            else -> null
        }
}
