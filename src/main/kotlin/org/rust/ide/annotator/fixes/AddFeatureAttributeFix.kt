/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.name

class AddFeatureAttributeFix(
    private val featureName: String,
    crateRoot: RsMod
) : LocalQuickFixAndIntentionActionOnPsiElement(crateRoot) {

    override fun getFamilyName(): String = "Add feature attribute"
    override fun getText(): String = "Add `$featureName` feature"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val mod = startElement as RsMod
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
