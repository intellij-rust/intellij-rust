package org.toml.ide

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory

class TomlHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {

    override fun createHighlighter(): TomlHighlighter {
        return TomlHighlighter()
    }

}

