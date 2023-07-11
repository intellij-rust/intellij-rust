/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.text.EditDistance
import org.rust.RsBundle

/**
 * Changes the text of some element to the suggested name using the provided function.
 */
class NameSuggestionFix<T : PsiElement>(
    element: T,
    private val newName: String,
    @SafeFieldForPreview
    private val elementFactory: (name: String) -> T
): RsQuickFixBase<T>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.change.name.element")
    override fun getText(): String = RsBundle.message("intention.name.change.to", newName)

    override fun invoke(project: Project, editor: Editor?, element: T) {
        val newElement = elementFactory(newName)
        element.replace(newElement)
    }

    companion object {
        fun <T : PsiElement> createApplicable(
            element: T,
            name: String,
            validNames: List<String>,
            maxDistance: Int,
            elementFactory: (name: String) -> T,
        ): List<NameSuggestionFix<T>> {
            val applicableNames = validNames.filter {
                name != it && EditDistance.levenshtein(it, name, true) <= maxDistance
            }
            return applicableNames.map { NameSuggestionFix(element, it, elementFactory) }
        }
    }
}
