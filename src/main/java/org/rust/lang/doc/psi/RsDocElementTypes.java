package org.rust.lang.doc.psi;

public interface RsDocElementTypes {
    RsDocTokenType DOC_TEXT = new RsDocTokenType("<DOC_TEXT>");

    RsDocTokenType DOC_HEADING = new RsDocTokenType("<DOC_HEADING>");
    RsDocTokenType DOC_INLINE_LINK = new RsDocTokenType("<DOC_INLINE_LINK>");
    RsDocTokenType DOC_REF_LINK = new RsDocTokenType("<DOC_REF_LINK>");
    RsDocTokenType DOC_LINK_REF_DEF = new RsDocTokenType("<DOC_LINK_REF_DEF>");
    RsDocTokenType DOC_CODE_SPAN = new RsDocTokenType("<DOC_CODE_SPAN>");
    RsDocTokenType DOC_CODE_FENCE = new RsDocTokenType("<DOC_CODE_FENCE>");
}
