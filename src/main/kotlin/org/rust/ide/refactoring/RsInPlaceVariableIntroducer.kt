/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer

class RsInPlaceVariableIntroducer(
    elementToRename: PsiNamedElement,
    editor: Editor,
    project: Project,
    title: String,
    private val additionalElementsToRename: List<PsiElement> = emptyList()
) : InplaceVariableIntroducer<PsiElement>(elementToRename, editor, project, title, emptyArray(), null) {
    override fun collectAdditionalElementsToRename(stringUsages: MutableList<Pair<PsiElement, TextRange>>) {
        for (element in additionalElementsToRename) {
            if (element.isValid) {
                stringUsages.add(Pair(element, TextRange(0, element.textLength)))
            }
        }
    }
}
