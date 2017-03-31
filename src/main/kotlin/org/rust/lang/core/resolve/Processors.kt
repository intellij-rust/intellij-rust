package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.ext.RsCompositeElement

interface Variant {
    val name: String
    val element: RsCompositeElement?
}

// FOUND/STOP == true
typealias RsResolveProcessor = (Variant) -> Boolean


class MultiResolveProcessor(private val referenceName: String) : RsResolveProcessor {
    private val result = mutableListOf<RsCompositeElement>()

    override fun invoke(v: Variant): Boolean {
        if (v.name == referenceName) {
            val element = v.element ?: return false
            result += element
        }
        return false
    }

    fun run(f: (RsResolveProcessor) -> Unit): List<RsCompositeElement> {
        f(this)
        return result
    }
}

class CompletionProcessor : RsResolveProcessor {
    private val result = mutableListOf<LookupElement>()

    override fun invoke(v: Variant): Boolean {
        val lookupElement = v.element?.createLookupElement(v.name)
        if (lookupElement != null) {
            result += lookupElement
        }
        return false
    }

    fun run(f: (RsResolveProcessor) -> Unit): Array<LookupElement> {
        f(this)
        return result.toTypedArray()
    }
}
