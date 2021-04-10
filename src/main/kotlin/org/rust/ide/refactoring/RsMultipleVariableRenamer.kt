/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.ide.DataManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer

class RsMultipleVariableRenamer(
    private val elementsToRename: List<SmartPsiElementPointer<PsiNamedElement>>,
): VariableInplaceRenameHandler() {
    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer {
        return object: VariableInplaceRenamer(elementToRename as PsiNamedElement, editor, elementToRename.project) {
            override fun performRefactoring(): Boolean {
                try {
                    return super.performRefactoring()
                }
                finally {
                    if (elementsToRename.size > 1) {
                        val element = elementsToRename[1].element
                        if (element != null) {
                            val offset = element.textRange?.startOffset
                            if (offset != null) {
                                editor.caretModel.moveToOffset(offset)
                                RsMultipleVariableRenamer(elementsToRename.drop(1)).doRename(
                                    element, editor, DataManager.getInstance().getDataContext(editor.component))
                            }
                        }
                    }
                }
            }
        }
    }
}
