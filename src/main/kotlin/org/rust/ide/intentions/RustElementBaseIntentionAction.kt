package org.rust.ide.intentions

import backcompat.runWriteAction
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class RustElementBaseIntentionAction : PsiElementBaseIntentionAction() {

    // BACKCOMPAT: 2016.3
    //
    // Some files may be readonly. It's not the end of the world --- the action
    // just needs to show "make writable" dialog in the `invoke` call. In 2017.1 this is handled
    // automagically: `getElementToMakeWritable` returns some PSI element (the file by default),
    // and IDE ensures that it is not readonly.
    //
    // We don't have that luxury yet, so we need to do this manually. It is forbidden to show
    // GUI dialogs in write action, so we must take care to show dialog first, and run the write
    // action afterwards.
    final override fun startInWriteAction(): Boolean = false

    final override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return
        runWriteAction {
            invokeImpl(project, editor, element)
        }
    }
    abstract fun invokeImpl(project: Project, editor: Editor, element: PsiElement)

    abstract override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean
}
