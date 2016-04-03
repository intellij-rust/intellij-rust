package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustUseGlob
import org.rust.lang.core.resolve.RustResolveEngine

class RustUseGlobReferenceImpl(useGlob: RustUseGlob)
    : PsiReferenceBase<RustUseGlob>(useGlob, useGlob.identifierRange, /* soft = */ false)
    , RustReference {

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.complete(element)

    override fun resolve(): RustNamedElement? {
        return RustResolveEngine.resolveUseGlob(element).element
    }
}


private val RustUseGlob.identifierRange: TextRange
    get() {
        // NOTE: EMPTY_RANGE case is impossible
        val name = (identifier ?: self) ?: return TextRange.EMPTY_RANGE
        return TextRange.from(name.startOffsetInParent, name.textLength)
    }
