package org.rust.lang.refactoring.generateConstructor



import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.ancestorOrSelf

class GenerateConstructorAction : CodeInsightAction(){
    override fun getHandler(): CodeInsightActionHandler = GenerateConstructorHandler()
    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        return GenerateConstructorHandler().isValidFor(editor,file)
    }
}
class GenerateConstructorHandler : LanguageCodeInsightActionHandler{

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (file !is RsFile) return false

        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classOrObject = elementAtCaret?.ancestorOrSelf<RsStructItem>()
        return classOrObject != null
    }

    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classItem = elementAtCaret?.ancestorOrSelf<RsStructItem>()
            ?: return
        generateConstructorBody(classItem, editor)
    }
}
