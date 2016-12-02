package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.types.util.resolvedType

/**
 * Navigate the implementation of the selected struct
 * (works if the impl is in other files)
 *
 * ```
 * struct <caret>MyStruct {
 * }
 *
 * impl MyStruct {
 * }
 * ```
 *
 * to this:
 *
 * ```
 * struct MyStruct {
 * }
 *
 * <caret>impl MyStruct {
 * }
 * ```
 */
class NavigateToImplementationIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Navigate to implementation"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val struct = element.parentOfType<RustStructItemElement>() ?: return
        val impl = RustImplIndex.findImplsFor(struct.resolvedType, project).elementAt(0)

        OpenFileDescriptor(project, impl.containingFile.virtualFile, impl.textOffset).navigateInEditor(project, true)
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val struct = element.parentOfType<RustStructItemElement>() ?: return false
        val impl = RustImplIndex.findImplsFor(struct.resolvedType, project)

        return impl.count() != 0
    }
}
