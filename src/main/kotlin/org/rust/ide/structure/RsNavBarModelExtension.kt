/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsElement

/** Shows nav bar for items from structure view [RsStructureViewModel] */
class RsNavBarModelExtension : StructureAwareNavBarModelExtension() {
    override val language: Language = RsLanguage

    override fun createModel(file: PsiFile, editor: Editor?): StructureViewModel? {
        if (file !is RsFile) return null
        return RsStructureViewModel(editor, file, expandMacros = false)
    }

    override fun getPresentableText(item: Any?): String? {
        val element = item as? RsElement ?: return null
        if (element is RsFile) {
            return element.name
        }

        val provider = RsBreadcrumbsInfoProvider()
        return provider.getBreadcrumb(element)
    }

    /** When [getPresentableText] returns null, [PsiElement.getText] will be used, and we want to avoid it */
    override fun getLeafElement(dataContext: DataContext): PsiElement? {
        val leafElement = super.getLeafElement(dataContext) as? RsElement ?: return null
        if (RsBreadcrumbsInfoProvider().getBreadcrumb(leafElement) == null) return null
        return leafElement
    }
}
