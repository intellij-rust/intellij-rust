package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.ext.RsCompositeElement

// FOUND/STOP == true
typealias RsResolveProcessor = (Variant) -> Boolean


class MultiResolveProcessor(private val referenceName: String) : RsResolveProcessor {
    val result = mutableListOf<RsCompositeElement>()
    override fun invoke(v: Variant): Boolean {
        if (v.name == referenceName) result += v.element
        return false
    }
}

class CompletionProcessor() : RsResolveProcessor {
    val result = mutableListOf<LookupElement>()
    override fun invoke(v: Variant): Boolean {
        result += v.element.createLookupElement(v.name)
        return false
    }
}
