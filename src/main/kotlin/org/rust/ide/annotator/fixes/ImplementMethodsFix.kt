package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPsiFactory

/**
 * Adds empty impelementations of the given methods to an impl block.
 */
class ImplementMethodsFix(
    implBody: RustImplItemElement,
    private val methods: List<RustFunctionElement>
) : LocalQuickFixAndIntentionActionOnPsiElement(implBody) {
    init {
        check(methods.isNotEmpty())
    }

    override fun getText(): String = "Implement methods"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val impl = (startElement as RustImplItemElement)
        val templateImpl = RustPsiFactory(project).createTraitImplItem(methods)
        val lastMethodOrBrace = impl.functionList.lastOrNull() ?: impl.lbrace ?: return
        val firstToAdd = templateImpl.functionList.first()
        val lastToAdd = templateImpl.functionList.last()
        impl.addRangeAfter(firstToAdd, lastToAdd, lastMethodOrBrace)
    }

}
