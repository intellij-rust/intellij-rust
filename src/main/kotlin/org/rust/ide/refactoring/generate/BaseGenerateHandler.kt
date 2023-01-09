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
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.RsCachedImplItem
import org.rust.lang.core.types.*
import org.rust.lang.core.types.rawType
import org.rust.openapiext.checkWriteAccessNotAllowed
import org.rust.openapiext.filterQuery

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
        val implBlocks: Collection<RsImplItem> = emptyList()
    )

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = getContext(editor, file) != null

    override fun startInWriteAction() = false

    open val allowEmptySelection: Boolean = false

    private fun getContext(editor: Editor, file: PsiFile): Context? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null

        val structItem = element.ancestorOrSelf<RsStructItem>()
        val (struct, substitution) = if (structItem == null) {
            val impl = element.ancestorStrict<RsItemElement>() as? RsImplItem ?: return null

            if (!isImplBlockValid(impl)) return null

            val struct = (impl.typeReference?.skipParens() as? RsPathType)?.path?.reference?.resolve() as? RsStructItem ?: return null
            val substitution = impl.typeReference?.rawType?.typeParameterValues ?: emptySubstitution

            struct to substitution
        } else {
            structItem to emptySubstitution
        }
        if (!isStructValid(struct)) return null

        val impls = struct.searchForImplementations().filterQuery { isImplBlockValid(it) }.findAll()

        val fields = StructMember.fromStruct(struct, substitution).filter { isFieldValid(it, impls) }
        if (fields.isEmpty() && !allowEmptyFields()) return null

        return Context(struct, fields, substitution, impls)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val context = getContext(editor, file) ?: return
        selectMembers(context, editor)
    }

    private fun selectMembers(context: Context, editor: Editor) {
        checkWriteAccessNotAllowed()

        val chosenFields = showStructMemberChooserDialog(
            context.struct.project,
            context.struct,
            context.fields,
            dialogTitle,
            allowEmptySelection
        ) ?: return
        runWriteAction {
            performRefactoring(context.struct, context.implBlocks.firstOrNull(), chosenFields, context.substitution, editor)
        }
    }

    protected fun getOrCreateImplBlock(
        implBlock: RsImplItem?,
        psiFactory: RsPsiFactory,
        structName: String,
        struct: RsStructItem
    ): RsImplItem {
        return if (implBlock == null) {
            val sibling = findSiblingImplItem(struct)
            if (sibling != null) return sibling

            val impl = psiFactory.createInherentImplItem(structName, struct.typeParameterList, struct.whereClause)
            struct.parent.addAfter(impl, struct) as RsImplItem
        } else {
            implBlock
        }
    }

    protected open fun isImplBlockValid(impl: RsImplItem): Boolean = impl.traitRef == null
    protected open fun isStructValid(struct: RsStructItem): Boolean = true
    protected open fun isFieldValid(member: StructMember, impls: Collection<RsImplItem>): Boolean = true
    protected open fun allowEmptyFields(): Boolean = false

    protected abstract fun performRefactoring(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    )

    protected abstract val dialogTitle: @Suppress("UnstableApiUsage") @DialogTitle String
}

/**
 * Try to find an impl item that is a sibling of the given `struct`.
 * Works on a best effort basis, if the impl block has any generics, it will not be considered.
 */
private fun findSiblingImplItem(struct: RsStructItem): RsImplItem? {
    return (struct.contextStrict<RsItemsOwner>())
        ?.childrenOfType<RsImplItem>()
        ?.firstOrNull { impl ->
            val cachedImpl = RsCachedImplItem.forImpl(impl)
            val (type, generics, constGenerics) = cachedImpl.typeAndGenerics ?: return@firstOrNull false
            cachedImpl.isInherent && cachedImpl.isValid && !cachedImpl.isNegativeImpl
                && generics.isEmpty() && constGenerics.isEmpty()  // TODO: Support generics
                && type.isEquivalentTo(struct.declaredType)
        }
}
