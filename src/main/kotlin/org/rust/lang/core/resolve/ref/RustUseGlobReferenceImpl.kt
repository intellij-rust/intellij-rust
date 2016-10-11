package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustUseGlobReferenceImpl(
    useGlob: RustUseGlobElement
) : RustReferenceBase<RustUseGlobElement>(useGlob),
    RustReference {

    override val RustUseGlobElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.completeUseGlob(element)

    override fun resolveInner(): List<RustNamedElement> = listOfNotNull(RustResolveEngine.resolveUseGlob(element))
}


