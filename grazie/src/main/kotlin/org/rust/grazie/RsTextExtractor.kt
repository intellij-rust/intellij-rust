/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain.*
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind
import org.rust.lang.doc.psi.RsDocComment
import org.rust.lang.doc.psi.RsDocElementTypes
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_CODE_FENCE

class RsTextExtractor : TextExtractor() {

    override fun buildTextContent(element: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
        return when {
            LITERALS in allowedDomains && element is RsLitExpr && element.kind is RsLiteralKind.String -> {
                TextContentBuilder.FromPsi.build(element, LITERALS)
            }
            DOCUMENTATION in allowedDomains && element is RsDocComment-> {
                DOCUMENTATION_CONTENT_BUILDER.build(element, DOCUMENTATION)
            }
            COMMENTS in allowedDomains && element is PsiCommentImpl -> {
                // Allows extracting single text from a sequence of line comments
                val siblings = getNotSoDistantSimilarSiblings(element) { it.elementType == element.elementType }
                TextContent.joinWithWhitespace('\n', siblings.mapNotNull { COMMENT_CONTENT_BUILDER.build(it, COMMENTS) })
            }
            else -> null
        }
    }
}

private val DOCUMENTATION_CONTENT_BUILDER: TextContentBuilder = TextContentBuilder.FromPsi
    .excluding { it.elementType in EXCLUDED_DOCUMENTATION_TOKENS }
    .withUnknown { it.elementType == DOC_CODE_FENCE }
    .removingIndents(" \t")
    .removingLineSuffixes(" \t")

private val COMMENT_CONTENT_BUILDER: TextContentBuilder = TextContentBuilder.FromPsi
    .removingIndents(" \t/*")
    .removingLineSuffixes(" \t/*")

private val EXCLUDED_DOCUMENTATION_TOKENS = TokenSet.create(
    RsDocElementTypes.DOC_GAP
)
