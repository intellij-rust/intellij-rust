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
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeReference

private const val FAMILY_NAME: String = "Convert to Sized type"

abstract class ConvertToSizedTypeFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getFamilyName(): String = FAMILY_NAME

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, typeReference: PsiElement, endElement: PsiElement) {
        if (typeReference !is RsTypeReference) return
        val factory = RsPsiFactory(project)
        val newTypeReference = newTypeReference(factory, typeReference)
        typeReference.replace(newTypeReference)
    }

    protected abstract fun newTypeReference(factory: RsPsiFactory, typeReference: RsTypeReference): RsTypeReference
}

class ConvertToReferenceFix(element: PsiElement) : ConvertToSizedTypeFix(element) {

    override fun getText(): String = "Convert to reference"

    override fun newTypeReference(factory: RsPsiFactory, typeReference: RsTypeReference): RsTypeReference =
        factory.createType("&${typeReference.text}")
}

class ConvertToBoxFix(element: PsiElement) : ConvertToSizedTypeFix(element) {

    override fun getText(): String = "Convert to Box"

    override fun newTypeReference(factory: RsPsiFactory, typeReference: RsTypeReference): RsTypeReference =
        factory.createType("Box<${typeReference.text}>")
}
