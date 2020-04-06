/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generateConstructor


import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.checkWriteAccessNotAllowed

class GenerateConstructorAction : CodeInsightAction() {

    private val generateConstructorHandler: GenerateConstructorHandler = GenerateConstructorHandler()

    override fun getHandler(): CodeInsightActionHandler = generateConstructorHandler

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
        generateConstructorHandler.isValidFor(editor, file)
}

private data class Context(val struct: RsStructItem, val implBlock: RsImplItem? = null)

class GenerateConstructorHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = getContext(editor, file) != null

    override fun startInWriteAction() = false
    private fun getContext(editor: Editor, file: PsiFile): Context? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val struct = element.ancestorOrSelf<RsStructItem>()
        return if (struct != null) {
            Context(struct)
        } else {
            val impl = element.ancestorOrSelf<RsImplItem>() ?: return null

            // Filter out blocks already containing a constructor.
            // This is just a best effort filter that doesn't consider all impl blocks of the struct.
            if (!impl.isSuitableForConstructor) return null

            val structRef = (impl.typeReference?.skipParens() as? RsBaseType)?.path?.reference?.resolve() as? RsStructItem ?: return null
            Context(structRef, impl)
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val context = getContext(editor, file) ?: return
        generateConstructorBody(context, editor)
    }

    private fun generateConstructorBody(context: Context, editor: Editor) {
        checkWriteAccessNotAllowed()
        val substitution = context.implBlock?.typeReference?.type?.typeParameterValues ?: emptySubstitution
        val chosenFields = showConstructorArgumentsChooser(context.struct.project, context.struct, substitution)
            ?: return
        runWriteAction {
            insertNewConstructor(context.struct, context.implBlock, chosenFields, substitution, editor)
        }
    }

    private fun insertNewConstructor(
        structItem: RsStructItem,
        implBlock: RsImplItem?,
        selectedFields: List<ConstructorArgument>,
        substitution: Substitution,
        editor: Editor
    ) {
        checkWriteAccessAllowed()
        val project = editor.project ?: return
        val structName = structItem.name ?: return
        val psiFactory = RsPsiFactory(project)
        val impl = if (implBlock == null) {
            val impl = psiFactory.createInherentImplItem(structName, structItem.typeParameterList, structItem.whereClause)
            structItem.parent.addAfter(impl, structItem) as RsImplItem
        } else {
            implBlock
        }

        val anchor = impl.lastChild.lastChild
        val constructor = createConstructor(structItem, selectedFields, psiFactory, substitution)
        impl.lastChild.addBefore(constructor, anchor)
        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }

    private fun createConstructor(
        structItem: RsStructItem,
        selectedFields: List<ConstructorArgument>,
        psiFactory: RsPsiFactory,
        substitution: Substitution
    ): RsFunction {
        val arguments = selectedFields.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "${it.argumentIdentifier}: ${it.typeReferenceText}"
        }

        val body = generateBody(structItem, selectedFields, substitution)
        return psiFactory.createTraitMethodMember("pub fn new$arguments->Self{\n$body}\n")
    }

    private fun generateBody(
        structItem: RsStructItem,
        selectedFields: List<ConstructorArgument>,
        substitution: Substitution
    ): String {
        val prefix = if (structItem.isTupleStruct) "(" else "{"
        val postfix = if (structItem.isTupleStruct) ")" else "}"
        val arguments = ConstructorArgument.fromStruct(structItem, substitution).joinToString(prefix = prefix, postfix = postfix, separator = ",") {
            if (it !in selectedFields) it.fieldIdentifier else it.argumentIdentifier
        }
        return structItem.nameIdentifier?.text + arguments
    }
}

data class ConstructorArgument(
    val argumentIdentifier: String,
    val fieldIdentifier: String,
    val typeReferenceText: String
) {

    val dialogRepresentation: String get() = "$argumentIdentifier: $typeReferenceText"

    companion object {
        fun fromStruct(structItem: RsStructItem, substitution: Substitution): List<ConstructorArgument> {
            return if (structItem.isTupleStruct) {
                fromTupleList(structItem.positionalFields, substitution)
            } else {
                fromFieldList(structItem.namedFields, substitution)
            }
        }

        private fun fromTupleList(tupleFieldList: List<RsTupleFieldDecl>, substitution: Substitution): List<ConstructorArgument> {
            return tupleFieldList.mapIndexed { index, tupleField ->
                val typeName = tupleField.typeReference.substAndGetText(substitution)
                ConstructorArgument("field$index", "()", typeName)
            }
        }

        private fun fromFieldList(fieldDeclList: List<RsNamedFieldDecl>, substitution: Substitution): List<ConstructorArgument> {
            return fieldDeclList.map {
                ConstructorArgument(
                    it.identifier.text ?: "()",
                    it.identifier.text + ":()",
                    it.typeReference?.substAndGetText(substitution) ?: "()"
                )
            }
        }
    }
}

private val RsImplItem.isSuitableForConstructor: Boolean
    get() = this.traitRef == null && this.members?.childrenOfType<RsFunction>()?.find { it.name == "new" } == null
