/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.doc.psi.RsDocCodeFence
import org.rust.lang.doc.psi.RsDocComment
import org.rust.lang.doc.psi.RsDocLinkDefinition

/**
 * @param text a text for lazy parsing. `null` value means that the element is parsed ([isParsed] is `true`)
 */
class RsDocCommentImpl(type: IElementType, text: CharSequence?) : LazyParseablePsiElement(type, text), RsDocComment {
    override fun getTokenType(): IElementType = elementType

    /** Needed for URL references ([com.intellij.openapi.paths.WebReference]) */
    override fun getReferences(): Array<PsiReference> {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this)
    }

    // Needed for RsFoldingBuilder
    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitComment(this);
    }

    override fun toString(): String {
        return "PsiComment($elementType)"
    }

    override fun getOwner(): RsDocAndAttributeOwner? = ancestorStrict()

    override val codeFences: List<RsDocCodeFence>
        get() = childrenOfType()

    override val linkDefinitions: List<RsDocLinkDefinition>
        get() = childrenOfType()

    override val linkReferenceMap: Map<String, RsDocLinkDefinition>
        get() = CachedValuesManager.getCachedValue(this) {
            val result = linkDefinitions.associateBy { it.linkLabel.markdownValue }
            CachedValueProvider.Result(result, containingFile)
        }
}
