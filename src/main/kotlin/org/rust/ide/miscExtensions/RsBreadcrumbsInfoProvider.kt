package org.rust.ide.miscExtensions

import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider
import org.rust.ide.utils.breadcrumbName
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.ext.RsCompositeElement


class RsBreadcrumbsInfoProvider : BreadcrumbsInfoProvider() {
    override fun getLanguages(): Array<RsLanguage> = LANGUAGES

    override fun acceptElement(e: PsiElement): Boolean =
        e is RsCompositeElement && breadcrumbName(e) != null

    override fun getElementInfo(e: PsiElement): String = breadcrumbName(e as RsCompositeElement)!!

    override fun getElementTooltip(e: PsiElement): String? = null

    companion object {
        private val LANGUAGES = arrayOf(RsLanguage)
    }
}
