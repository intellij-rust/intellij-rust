package org.rust.ide.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType

class RustCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, DeriveCompletionProvider.elementPattern, DeriveCompletionProvider)
    }
}
