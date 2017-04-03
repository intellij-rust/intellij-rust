package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.ext.RsCompositeElement

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsCompositeElement?
}

/**
 * true means that an interesting element was found
 * and that all further processing should stop.
 */
typealias RsResolveProcessor = (ScopeEntry) -> Boolean


class MultiResolveProcessor(private val referenceName: String) : RsResolveProcessor {
    fun run(f: (RsResolveProcessor) -> Unit): List<RsCompositeElement> {
        f(this)
        return result
    }

    private val result = mutableListOf<RsCompositeElement>()

    override fun invoke(v: ScopeEntry): Boolean {
        if (v.name == referenceName) {
            val element = v.element ?: return false
            result += element
        }
        return false
    }
}

class CompletionProcessor : RsResolveProcessor {
    fun run(f: (RsResolveProcessor) -> Unit): Array<LookupElement> {
        f(this)
        return result.toTypedArray()
    }

    private val result = mutableListOf<LookupElement>()

    override fun invoke(v: ScopeEntry): Boolean {
        val lookupElement = v.element?.createLookupElement(v.name)
        if (lookupElement != null) {
            result += lookupElement
        }
        return false
    }
}
