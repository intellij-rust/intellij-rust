/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.util.text.EditDistance
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Changes the text of some element to the suggested name using the provided function.
 */
class NameSuggestionFix<T : PsiElement>(
    element: T,
    private val newName: String,
    private val elementFactory: (name: String) -> T
): LocalQuickFixOnPsiElement(element) {
    override fun getFamilyName(): String = "Change name of element"
    override fun getText(): String = "Change to `$newName`"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val newElement = elementFactory(newName)
        startElement.replace(newElement)
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
