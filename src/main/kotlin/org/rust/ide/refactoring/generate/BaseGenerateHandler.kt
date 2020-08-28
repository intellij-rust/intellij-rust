/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate


import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.skipParens
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessNotAllowed

abstract class BaseGenerateHandler : LanguageCodeInsightActionHandler {
    data class Context(val struct: RsStructItem, val implBlock: RsImplItem? = null)

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = getContext(editor, file) != null

    override fun startInWriteAction() = false

    private fun getContext(editor: Editor, file: PsiFile): Context? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val struct = element.ancestorOrSelf<RsStructItem>()
        return if (struct != null) {
            Context(struct)
        } else {
            val impl = element.ancestorOrSelf<RsImplItem>() ?: return null

            if (!isImplBlockValid(impl)) return null

            val structRef = (impl.typeReference?.skipParens() as? RsBaseType)?.path?.reference?.resolve() as? RsStructItem
                ?: return null
            Context(structRef, impl)
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val context = getContext(editor, file) ?: return
        selectMembers(context, editor)
    }

    protected fun selectMembers(context: Context, editor: Editor) {
        checkWriteAccessNotAllowed()
        val substitution = context.implBlock?.typeReference?.type?.typeParameterValues ?: emptySubstitution
        val chosenFields = showStructMemberChooserDialog(
            context.struct.project,
            context.struct,
            substitution,
            dialogTitle
        ) ?: return
        runWriteAction {
            performRefactoring(context.struct, context.implBlock, chosenFields, substitution, editor)
        }
    }

    protected open fun isImplBlockValid(impl: RsImplItem): Boolean = true
    protected abstract fun performRefactoring(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    )

    protected abstract val dialogTitle: String
}
