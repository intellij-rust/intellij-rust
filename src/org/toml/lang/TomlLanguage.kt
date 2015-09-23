package org.toml.lang

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import org.toml.lang.TomlHighlighter


public class TomlLanguage : Language("TOML", "text/toml") {

    companion object {
        public val INSTANCE: TomlLanguage = TomlLanguage();
    }
}
