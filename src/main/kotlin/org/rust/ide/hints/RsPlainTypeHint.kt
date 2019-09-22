/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement

/** New interactive type hints are used instead */
enum class RsPlainTypeHint(desc: String, enabled: Boolean) : RsPlainHint {
    ;

    override fun isApplicable(elem: PsiElement): Boolean = false
    override fun provideHints(elem: PsiElement): List<InlayInfo> = emptyList()
    override val enabled: Boolean = false
    override val option: Option = Option("SHOW_${this.name}", desc, enabled)
}
