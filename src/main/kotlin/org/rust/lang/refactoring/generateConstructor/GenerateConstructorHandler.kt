/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.generateConstructor


import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.openapiext.checkWriteAccessAllowed

class GenerateConstructorAction : CodeInsightAction() {
    override fun getHandler(): CodeInsightActionHandler = generateConstructorHandler
    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
        GenerateConstructorHandler().isValidFor(editor, file)

    private val generateConstructorHandler = GenerateConstructorHandler()
}

class GenerateConstructorHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean =
        getStructItem(editor, file) != null

    override fun startInWriteAction() = false
    private fun getStructItem(editor: Editor, file: PsiFile): RsStructItem? =
        file.findElementAt(editor.caretModel.offset)?.ancestorOrSelf()

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val structItem = getStructItem(editor, file) ?: return
        generateConstructorBody(structItem, editor)
    }


    private fun generateConstructorBody(structItem: RsStructItem, editor: Editor) {
        check(!ApplicationManager.getApplication().isWriteAccessAllowed)
        val chosenFields = showConstructorArgumentsChooser(structItem, structItem.project) ?: return
        runWriteAction {
            insertNewConstructor(structItem, chosenFields, editor)
        }
    }

    private fun insertNewConstructor(structItem: RsStructItem, selectedFields: List<ConstructorArgument>, editor: Editor) {
        checkWriteAccessAllowed()
        val project = editor.project ?: return
        val psiFactory = RsPsiFactory(project)
        val structName = structItem.name ?: return
        var expr = psiFactory.createInherentImplItem(structName, structItem.typeParameterList, structItem.whereClause)
        val anchor = expr.lastChild.lastChild
        val function = getFunction(structItem, selectedFields, psiFactory)
        expr.lastChild.addBefore(function, anchor)
        expr = structItem.parent.addAfter(expr, structItem) as RsImplItem
        editor.caretModel.moveToOffset(expr.textOffset + expr.textLength - 1)
    }

    private fun getFunction(structItem: RsStructItem, selectedFields: List<ConstructorArgument>, psiFactory: RsPsiFactory): RsFunction {
        val arguments = buildString {
            append(selectedFields.joinToString(prefix = "(", postfix = ")", separator = ",")
            { "${it.argumentIdentifier}:${(it.typeReference)}" })
        }

        val body = generateBody(structItem, selectedFields)
        return psiFactory.createTraitMethodMember("pub fn new$arguments->Self{\n$body}\n")
    }


    private fun generateBody(structItem: RsStructItem, selectedFields: List<ConstructorArgument>): String {
        val prefix = if (structItem.isTupleStruct) "(" else "{"
        val postfix = if (structItem.isTupleStruct) ")" else "}"
        return structItem.nameIdentifier?.text + ConstructorArgument.fromStruct(structItem).joinToString(prefix = prefix, postfix = postfix, separator = ",") {
            if (!selectedFields.contains(it)) {
                it.fieldIdentifier
            } else {
                it.argumentIdentifier
            }
        }
    }
}

data class ConstructorArgument(val argumentIdentifier: String,
                               val fieldIdentifier: String,
                               val typeReference: String,
                               val type: RsTypeReference?) {
    companion object {
        private fun fromTupleList(tupleFieldList: List<RsTupleFieldDecl>): List<ConstructorArgument> {
            return tupleFieldList.mapIndexed { index: Int, tupleField: RsTupleFieldDecl ->
                val typeName = (tupleField.typeReference.text ?: "()")
                ConstructorArgument("field$index", "()", typeName, tupleField.typeReference)
            }
        }

        fun fromStruct(structItem: RsStructItem): List<ConstructorArgument> {
            return if (structItem.isTupleStruct) {
                fromTupleList(structItem.tupleFields?.tupleFieldDeclList.orEmpty())
            } else {
                fromFieldList(structItem.blockFields?.fieldDeclList.orEmpty())
            }
        }

        private fun fromFieldList(fieldDeclList: List<RsFieldDecl>): List<ConstructorArgument> {
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
