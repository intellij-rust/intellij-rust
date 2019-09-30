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
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.psi.ext.positionalFields
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.checkWriteAccessNotAllowed

class GenerateConstructorAction : CodeInsightAction() {

    private val generateConstructorHandler: GenerateConstructorHandler = GenerateConstructorHandler()

    override fun getHandler(): CodeInsightActionHandler = generateConstructorHandler

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
        generateConstructorHandler.isValidFor(editor, file)
}

class GenerateConstructorHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = getStructItem(editor, file) != null

    override fun startInWriteAction() = false
    private fun getStructItem(editor: Editor, file: PsiFile): RsStructItem? =
        file.findElementAt(editor.caretModel.offset)?.ancestorOrSelf()

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val structItem = getStructItem(editor, file) ?: return
        generateConstructorBody(structItem, editor)
    }

    private fun generateConstructorBody(structItem: RsStructItem, editor: Editor) {
        checkWriteAccessNotAllowed()
        val chosenFields = showConstructorArgumentsChooser(structItem.project, structItem) ?: return
        runWriteAction {
            insertNewConstructor(structItem, chosenFields, editor)
        }
    }

    private fun insertNewConstructor(structItem: RsStructItem, selectedFields: List<ConstructorArgument>, editor: Editor) {
        checkWriteAccessAllowed()
        val project = editor.project ?: return
        val structName = structItem.name ?: return
        val psiFactory = RsPsiFactory(project)
        val impl = psiFactory.createInherentImplItem(structName, structItem.typeParameterList, structItem.whereClause)
        val anchor = impl.lastChild.lastChild
        val constructor = getFunction(structItem, selectedFields, psiFactory)
        impl.lastChild.addBefore(constructor, anchor)
        val insertedImpl = structItem.parent.addAfter(impl, structItem) as RsImplItem
        editor.caretModel.moveToOffset(insertedImpl.textOffset + insertedImpl.textLength - 1)
    }

    private fun getFunction(structItem: RsStructItem, selectedFields: List<ConstructorArgument>, psiFactory: RsPsiFactory): RsFunction {
        val arguments = selectedFields.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "${it.argumentIdentifier}: ${it.typeReference}"
        }

        val body = generateBody(structItem, selectedFields)
        return psiFactory.createTraitMethodMember("pub fn new$arguments->Self{\n$body}\n")
    }

    private fun generateBody(structItem: RsStructItem, selectedFields: List<ConstructorArgument>): String {
        val prefix = if (structItem.isTupleStruct) "(" else "{"
        val postfix = if (structItem.isTupleStruct) ")" else "}"
        val arguments = ConstructorArgument.fromStruct(structItem).joinToString(prefix = prefix, postfix = postfix, separator = ",") {
            if (it !in selectedFields) it.fieldIdentifier else it.argumentIdentifier
        }
        return structItem.nameIdentifier?.text + arguments
    }
}

data class ConstructorArgument(
    val argumentIdentifier: String,
    val fieldIdentifier: String,
    val typeReference: String,
    val type: RsTypeReference?
) {

    val dialogRepresentation: String get() = "$argumentIdentifier: ${type?.text ?: "()"}"

    companion object {
        fun fromStruct(structItem: RsStructItem): List<ConstructorArgument> {
            return if (structItem.isTupleStruct) {
                fromTupleList(structItem.positionalFields)
            } else {
                fromFieldList(structItem.namedFields)
            }
        }

        private fun fromTupleList(tupleFieldList: List<RsTupleFieldDecl>): List<ConstructorArgument> {
            return tupleFieldList.mapIndexed { index, tupleField ->
                val typeName = tupleField.typeReference.text ?: "()"
                ConstructorArgument("field$index", "()", typeName, tupleField.typeReference)
            }
        }

        private fun fromFieldList(fieldDeclList: List<RsNamedFieldDecl>): List<ConstructorArgument> {
            return fieldDeclList.map {
                ConstructorArgument(
                    it.identifier.text ?: "()",
                    it.identifier.text + ":()",
                    it.typeReference?.text ?: "()",
                    it.typeReference
                )
            }
        }
    }
}
