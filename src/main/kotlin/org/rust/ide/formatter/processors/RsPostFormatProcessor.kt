/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import org.rust.ide.formatter.RustfmtExternalFormatProcessor
import org.rust.lang.core.psi.RsFile

/**
 * Used in a couple with [RustfmtExternalFormatProcessor].
 * Should be the only `postFormatProcessor` in the plugin
 * (see [RustfmtExternalFormatProcessor] docs for more details)
 */
class RsPostFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        if (source !is RsFile) return source

        return RsTrailingCommaFormatProcessor.processElement(source, settings)
    }

    @Suppress("UnstableApiUsage")
    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (source !is RsFile) return rangeToReformat

        return if (RustfmtExternalFormatProcessor.isActiveForFile(source)) {
            RustfmtExternalFormatProcessor.formatWithRustfmtOrBuiltinFormatter(
                source,
                rangeToReformat,
                canChangeWhiteSpacesOnly = false
            ) ?: rangeToReformat
        } else {
            // Builtin formatter has just been used, perform post-processing
            RsTrailingCommaFormatProcessor.processText(source, rangeToReformat, settings)
        }
    }
}
