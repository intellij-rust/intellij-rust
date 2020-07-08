package org.rust.lang.doc.psi;

/** Used for lexer-based highlighting of documentation comments. These elements are never used in the PSI */
public interface RsDocHighlightingElementTypes {
    RsDocHighlightingTokenType DOC_TEXT = new RsDocHighlightingTokenType("<DOC_TEXT>");

    RsDocHighlightingTokenType DOC_HEADING = new RsDocHighlightingTokenType("<DOC_HEADING>");
    RsDocHighlightingTokenType DOC_INLINE_LINK = new RsDocHighlightingTokenType("<DOC_INLINE_LINK>");
    RsDocHighlightingTokenType DOC_REF_LINK = new RsDocHighlightingTokenType("<DOC_REF_LINK>");
    RsDocHighlightingTokenType DOC_LINK_REF_DEF = new RsDocHighlightingTokenType("<DOC_LINK_REF_DEF>");
    RsDocHighlightingTokenType DOC_CODE_SPAN = new RsDocHighlightingTokenType("<DOC_CODE_SPAN>");
    RsDocHighlightingTokenType DOC_CODE_FENCE = new RsDocHighlightingTokenType("<DOC_CODE_FENCE>");
}
