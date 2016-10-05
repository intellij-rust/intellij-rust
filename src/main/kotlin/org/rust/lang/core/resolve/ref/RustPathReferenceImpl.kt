package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustPatStructElement
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

    override fun resolveVerbose(): RustResolveEngine.ResolveResult {
        val path = element.asRustPath ?: return RustResolveEngine.ResolveResult.Unresolved
        return RustResolveEngine.resolve(path, element, namespace)
    }

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
