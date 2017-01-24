package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory

/**
 * Adds empty impelementations of the given methods to an impl block.
 */
class ImplementMethodsFix(
    implBody: RsImplItem,
    private val methods: List<RsFunction>
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
        val impl = (startElement as RsImplItem)
        val templateImpl = RsPsiFactory(project).createTraitImplItem(methods)
        val lastMethodOrBrace = impl.functionList.lastOrNull() ?: impl.lbrace ?: return
        val firstToAdd = templateImpl.functionList.first()
        val lastToAdd = templateImpl.functionList.last()
        impl.addRangeAfter(firstToAdd, lastToAdd, lastMethodOrBrace)
    }

}
