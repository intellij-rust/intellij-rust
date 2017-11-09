/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.miscExtensions

import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.rust.ide.presentation.breadcrumbName
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.ext.RsElement


class RsBreadcrumbsInfoProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<RsLanguage> = LANGUAGES

    override fun acceptElement(e: PsiElement): Boolean =
        e is RsElement && breadcrumbName(e) != null

    override fun getElementInfo(e: PsiElement): String = breadcrumbName(e as RsElement)!!

    override fun getElementTooltip(e: PsiElement): String? = null

    companion object {
        private val LANGUAGES = arrayOf(RsLanguage)
    }
}
