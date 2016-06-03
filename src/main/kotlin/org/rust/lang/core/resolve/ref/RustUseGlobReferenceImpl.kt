package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustUseGlobReferenceImpl(useGlob: RustUseGlobElement)
    : RustReferenceBase<RustUseGlobElement>(useGlob, useGlob.identifierRange)
    , RustReference {

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.completeUseGlob(element)

    override fun resolveVerbose(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveUseGlob(element)
}


private val RustUseGlobElement.identifierRange: TextRange
    get() {
        // NOTE: EMPTY_RANGE case is impossible
        val name = (identifier ?: self) ?: return TextRange.EMPTY_RANGE
        return TextRange.from(name.startOffsetInParent, name.textLength)
    }
