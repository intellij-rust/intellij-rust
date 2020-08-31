/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate


import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.skipParens
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessNotAllowed

abstract class BaseGenerateAction : CodeInsightAction() {
    abstract val handler: BaseGenerateHandler

    override fun getHandler(): CodeInsightActionHandler = handler
    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
        handler.isValidFor(editor, file)
}

abstract class BaseGenerateHandler : LanguageCodeInsightActionHandler {
    data class Context(
        val struct: RsStructItem,
        val fields: List<StructMember>,
        val substitution: Substitution,
        val implBlock: RsImplItem? = null
    )

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = getContext(editor, file) != null

    override fun startInWriteAction() = false

    private fun getContext(editor: Editor, file: PsiFile): Context? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val struct = element.ancestorOrSelf<RsStructItem>()
        val (structItem, impl) = if (struct != null) {
            struct to null
        } else {
            val impl = element.ancestorStrict<RsItemElement>() as? RsImplItem ?: return null

            if (!isImplBlockValid(impl)) return null

            val structRef = (impl.typeReference?.skipParens() as? RsBaseType)?.path?.reference?.resolve() as? RsStructItem
                ?: return null
            structRef to impl
        }

        if (!isStructValid(structItem)) return null
        val substitution = impl?.typeReference?.type?.typeParameterValues ?: emptySubstitution
        val fields = StructMember.fromStruct(structItem, substitution).filter { isFieldValid(it, impl) }
        if (fields.isEmpty() && !allowEmptyFields()) return null

        return Context(structItem, fields, substitution, impl)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val context = getContext(editor, file) ?: return
        selectMembers(context, editor)
    }

    protected fun selectMembers(context: Context, editor: Editor) {
        checkWriteAccessNotAllowed()

        val chosenFields = showStructMemberChooserDialog(
            context.struct.project,
            context.struct,
            context.fields,
            dialogTitle
        ) ?: return
        runWriteAction {
            performRefactoring(context.struct, context.implBlock, chosenFields, context.substitution, editor)
        }
    }

    protected fun getOrCreateImplBlock(
        implBlock: RsImplItem?,
        psiFactory: RsPsiFactory,
        structName: String,
        struct: RsStructItem
    ): RsImplItem {
        return if (implBlock == null) {
            val impl = psiFactory.createInherentImplItem(structName, struct.typeParameterList, struct.whereClause)
            struct.parent.addAfter(impl, struct) as RsImplItem
        } else {
            implBlock
        }
    }

    protected open fun isImplBlockValid(impl: RsImplItem): Boolean = impl.traitRef == null
    protected open fun isStructValid(struct: RsStructItem): Boolean = true
    protected open fun isFieldValid(member: StructMember, impl: RsImplItem?): Boolean = true
    protected open fun allowEmptyFields(): Boolean = false

    protected abstract fun performRefactoring(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    )

    protected abstract val dialogTitle: String
}
