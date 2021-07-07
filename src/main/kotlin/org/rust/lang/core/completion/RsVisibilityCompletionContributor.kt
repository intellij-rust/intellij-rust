package org.rust.lang.core.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.RsPsiPattern.declarationPattern
import org.rust.lang.core.RsPsiPattern.inherentImplDeclarationPattern
import org.rust.lang.core.or

class RsVisibilityCompletionContributor: CompletionContributor() {
    init {
        extend(CompletionType.BASIC, RsPsiPattern.fieldVisibility, RsVisibilityCompletionProvider())
        extend(CompletionType.BASIC, declarationPattern() or inherentImplDeclarationPattern(), RsVisibilityCompletionProvider())
    }
}
