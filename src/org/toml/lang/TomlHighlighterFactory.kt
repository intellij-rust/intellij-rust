package org.toml.lang

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import org.toml.lang.TomlHighlighter

public class TomlHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {

    override fun createHighlighter(): TomlHighlighter {
        return TomlHighlighter()
    }

}

