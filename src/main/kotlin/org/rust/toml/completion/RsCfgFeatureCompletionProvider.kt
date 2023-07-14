/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.completion.RsCompletionProvider
import org.rust.lang.core.completion.getElementOfType
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.elementType
import org.rust.toml.getPackageTomlFile
import org.rust.toml.resolve.allFeatures
import org.toml.lang.psi.TomlKeySegment

/**
 * Provides completion for cargo features in Rust cfg attributes:
 * ```
 * #[cfg(feature = "<caret>")]
 * fn foo() {}    //^ Provides completion here
 * ```
 *
 * @see org.rust.toml.resolve.RsCfgFeatureReferenceProvider
 */
object RsCfgFeatureCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val pkg = parameters.position.ancestorOrSelf<RsElement>()?.containingCargoPackage ?: return
        val pkgToml = pkg.getPackageTomlFile(parameters.originalFile.project) ?: return

        for (feature in pkgToml.allFeatures()) {
            result.addElement(rustLookupElementForFeature(feature))
        }
    }

    override val elementPattern: ElementPattern<out PsiElement>
        get() = RsPsiPattern.insideAnyCfgFeature
}

private fun rustLookupElementForFeature(feature: TomlKeySegment): LookupElementBuilder {
    return LookupElementBuilder
        .createWithSmartPointer(feature.text, feature)
        .withInsertHandler(RustStringLiteralInsertionHandler())
}

private class RustStringLiteralInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val leaf = context.getElementOfType<PsiElement>() ?: return
        val hasQuotes = leaf.parent is RsLitExpr && leaf.elementType in RS_ALL_STRING_LITERALS

        if (!hasQuotes) {
            context.document.insertString(context.startOffset, "\"")
            context.document.insertString(context.selectionEndOffset, "\"")
        }
    }
}
