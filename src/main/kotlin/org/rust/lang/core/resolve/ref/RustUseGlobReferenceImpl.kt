package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustUseGlobReferenceImpl(
    useGlob: RsUseGlob
) : RustReferenceBase<RsUseGlob>(useGlob),
    RustReference {

    override val RsUseGlob.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.completeUseGlob(element)

    override fun resolveInner(): List<RsCompositeElement> = RustResolveEngine.resolveUseGlob(element)
}


