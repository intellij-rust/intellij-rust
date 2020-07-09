/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import org.intellij.markdown.MarkdownElementTypes
import org.rust.lang.doc.psi.impl.*

@Suppress("MemberVisibilityCanBePrivate")
object RsDocElementTypes {
    val DOC_TEXT = RsDocTokenType("<DOC_TEXT>")
    val DOC_GAP = RsDocTokenType("<DOC_GAP>")

    val DOC_HEADING = RsDocCompositeTokenType("<DOC_HEADING>", ::RsDocHeadingImpl)

    val DOC_EMPHASIS = RsDocCompositeTokenType("<DOC_EMPHASIS>", ::RsDocEmphasisImpl)
    val DOC_STRONG = RsDocCompositeTokenType("<DOC_STRONG>", ::RsDocStrongImpl)
    val DOC_CODE_SPAN = RsDocCompositeTokenType("<DOC_CODE_SPAN>", ::RsDocCodeSpanImpl)

    val DOC_AUTO_LINK = RsDocCompositeTokenType("<DOC_AUTO_LINK>", ::RsDocAutoLinkImpl)
    val DOC_INLINE_LINK = RsDocCompositeTokenType("<DOC_INLINE_LINK>", ::RsDocInlineLinkImpl)
    val DOC_SHORT_REFERENCE_LINK = RsDocCompositeTokenType("<DOC_SHORT_REFERENCE_LINK>", ::RsDocLinkReferenceShortImpl)
    val DOC_FULL_REFERENCE_LINK = RsDocCompositeTokenType("<DOC_FULL_REFERENCE_LINK>", ::RsDocLinkReferenceFullImpl)
    val DOC_LINK_REF_DEFINITION = RsDocCompositeTokenType("<DOC_LINK_REF_DEFINITION>", ::RsDocLinkReferenceDefImpl)

    val DOC_LINK_TEXT = RsDocCompositeTokenType("<DOC_LINK_TEXT>", ::RsDocLinkTextImpl)
    val DOC_LINK_LABEL = RsDocCompositeTokenType("<DOC_LINK_LABEL>", ::RsDocLinkLabelImpl)
    val DOC_LINK_TITLE = RsDocCompositeTokenType("<DOC_LINK_TITLE>", ::RsDocLinkTitleImpl)
    val DOC_LINK_DESTINATION = RsDocCompositeTokenType("<DOC_LINK_DESTINATION>", ::RsDocLinkDestinationImpl)

    val DOC_CODE_FENCE = RsDocCompositeTokenType("<DOC_CODE_FENCE>", ::RsDocCodeFenceImpl)
    val DOC_CODE_BLOCK = RsDocCompositeTokenType("<DOC_CODE_BLOCK>", ::RsDocCodeBlockImpl)
    val DOC_BLOCK_QUOTE = RsDocCompositeTokenType("<DOC_BLOCK_QUOTE>", ::RsDocBlockQuoteImpl)
    val DOC_HTML_BLOCK = RsDocCompositeTokenType("<DOC_HTML_BLOCK>", ::RsDocHtmlBlockImpl)

    private val MARKDOWN_HEADERS = setOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2
    )

    fun map(type: org.intellij.markdown.IElementType): RsDocCompositeTokenType? {
        return when (type) {
            in MARKDOWN_HEADERS -> DOC_HEADING
            MarkdownElementTypes.EMPH -> DOC_EMPHASIS
            MarkdownElementTypes.STRONG -> DOC_STRONG
            MarkdownElementTypes.CODE_SPAN -> DOC_CODE_SPAN
            MarkdownElementTypes.AUTOLINK -> DOC_AUTO_LINK
            MarkdownElementTypes.INLINE_LINK -> DOC_INLINE_LINK
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> DOC_SHORT_REFERENCE_LINK
            MarkdownElementTypes.FULL_REFERENCE_LINK -> DOC_FULL_REFERENCE_LINK
            MarkdownElementTypes.LINK_DEFINITION -> DOC_LINK_REF_DEFINITION
            MarkdownElementTypes.LINK_TEXT -> DOC_LINK_TEXT
            MarkdownElementTypes.LINK_LABEL -> DOC_LINK_LABEL
            MarkdownElementTypes.LINK_TITLE -> DOC_LINK_TITLE
            MarkdownElementTypes.LINK_DESTINATION -> DOC_LINK_DESTINATION
            MarkdownElementTypes.CODE_FENCE -> DOC_CODE_FENCE
            MarkdownElementTypes.CODE_BLOCK -> DOC_CODE_BLOCK
            MarkdownElementTypes.BLOCK_QUOTE -> DOC_BLOCK_QUOTE
            MarkdownElementTypes.HTML_BLOCK -> DOC_HTML_BLOCK
            else -> null
        }
    }
}
