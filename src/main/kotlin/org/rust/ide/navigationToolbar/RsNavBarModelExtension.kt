/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigationToolbar

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.rust.ide.miscExtensions.RsBreadcrumbsInfoProvider
import org.rust.ide.structure.RsStructureViewModel
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsElement

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
}
