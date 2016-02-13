package org.rust.lang.core.psi;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface LiteralTokenTypes {
    /**
     * e.g. {@code b} in {@code b'x'}
     */
    IElementType PREFIX = new IElementType("LITERAL_PREFIX", Language.ANY);

    /**
     * e.g. {@code i32} in {@code 123i32}
     */
    IElementType SUFFIX = new IElementType("LITERAL_SUFFIX", Language.ANY);

    /**
     * e.g. {@code "} in {@code "foobar"}
     */
    IElementType DELIMITER = new IElementType("LITERAL_DELIMITER", Language.ANY);

    /**
     * e.g. {@code foobar} in {@code "foobar"} or {@code -123.0e6} in {@code -123.0e6f32}
     */
    IElementType VALUE = new IElementType("LITERAL_VALUE", Language.ANY);

    TokenSet LITERAL_TOKEN_TYPES = TokenSet.create(PREFIX, SUFFIX, DELIMITER, VALUE);
}
