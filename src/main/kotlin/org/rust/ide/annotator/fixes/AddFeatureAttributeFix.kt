/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.name

class AddFeatureAttributeFix(
    private val featureName: String,
    element: PsiElement
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getFamilyName(): String = "Add feature attribute"
    override fun getText(): String = "Add `$featureName` feature"

    // TODO: Add intention preview
    override fun getFileModifierForPreview(target: PsiFile): FileModifier? = null

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        addFeatureAttribute(project, startElement, featureName)
    }

    companion object {

        fun addFeatureAttribute(project: Project, context: PsiElement, featureName: String) {
            val mod = context.ancestorOrSelf<RsElement>()?.crateRoot ?: return
            val lastFeatureAttribute = mod.childrenOfType<RsInnerAttr>()
                .lastOrNull { it.metaItem.name == "feature" }

            val psiFactory = RsPsiFactory(project)
            val attr = psiFactory.createInnerAttr("feature($featureName)")
            if (lastFeatureAttribute != null) {
                mod.addAfter(attr, lastFeatureAttribute)
            } else {
                val insertedElement = mod.addBefore(attr, mod.firstChild)
                mod.addAfter(psiFactory.createNewline(), insertedElement)
            }
        }
    }
}
