/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement

class RsInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getSupportedOptions(): List<Option> =
        RsPlainHint.values.map { it.option } + RsPlainHint.SMART_HINTING

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun getHintInfo(element: PsiElement?): HintInfo? = null

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val resolved = RsPlainHint.resolve(element) ?: return emptyList()
        if (!resolved.enabled) return emptyList()
        return resolved.provideHints(element)
    }

    override fun getInlayPresentation(inlayText: String): String = inlayText
}
