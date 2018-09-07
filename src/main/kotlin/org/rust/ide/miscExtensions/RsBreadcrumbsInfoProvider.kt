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

    override fun acceptElement(element: PsiElement): Boolean =
        element is RsElement && breadcrumbName(element) != null

    override fun getElementInfo(element: PsiElement): String =
        breadcrumbName(element as RsElement)!!

    override fun getElementTooltip(element: PsiElement): String? = null

    companion object {
        private val LANGUAGES: Array<RsLanguage> = arrayOf(RsLanguage)
    }
}
