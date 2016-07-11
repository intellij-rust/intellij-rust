package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType

class RustCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, DeriveCompletionProvider.elementPattern, DeriveCompletionProvider)
        extend(CompletionType.BASIC, AttributeCompletionProvider.elementPattern, AttributeCompletionProvider)
    }
}
