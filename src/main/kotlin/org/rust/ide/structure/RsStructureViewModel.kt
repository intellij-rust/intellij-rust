/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile

class RsStructureViewModel(editor: Editor?, file: RsFile) : TextEditorBasedStructureViewModel(editor, file),
                                                            StructureViewModel.ElementInfoProvider {

    override fun getRoot() = RsModTreeElement(psiFile)

    override fun getPsiFile(): RsFile = super.getPsiFile() as RsFile

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = element.value is RsFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement) =
        when (element.value) {
            is RsFieldDecl,
            is RsFunction,
            is RsModDeclItem,
            is RsConstant,
            is RsTypeAlias -> true
            else -> false
        }
}
