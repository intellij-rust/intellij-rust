package org.rust.lang.doc.psi;

public interface RustDocElementTypes {
    RustDocTokenType DOC_TEXT = new RustDocTokenType("<DOC_TEXT>");
    RustDocTokenType DOC_DECO = new RustDocTokenType("<DOC_DECO>");

    RustDocTokenType DOC_HEADING = new RustDocTokenType("<DOC_HEADING>");
    RustDocTokenType DOC_INLINE_LINK = new RustDocTokenType("<DOC_INLINE_LINK>");
    RustDocTokenType DOC_REF_LINK = new RustDocTokenType("<DOC_REF_LINK>");
    RustDocTokenType DOC_LINK_REF_DEF = new RustDocTokenType("<DOC_LINK_REF_DEF>");
    RustDocTokenType DOC_CODE_SPAN = new RustDocTokenType("<DOC_CODE_SPAN>");
    RustDocTokenType DOC_CODE_FENCE = new RustDocTokenType("<DOC_CODE_FENCE>");
}
