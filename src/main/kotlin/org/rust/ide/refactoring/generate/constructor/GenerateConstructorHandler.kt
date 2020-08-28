/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate.constructor


import com.intellij.openapi.editor.Editor
import org.rust.ide.refactoring.generate.BaseGenerateAction
import org.rust.ide.refactoring.generate.BaseGenerateHandler
import org.rust.ide.refactoring.generate.StructMember
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.types.Substitution
import org.rust.openapiext.checkWriteAccessAllowed

class GenerateConstructorAction : BaseGenerateAction() {
    override val handler: BaseGenerateHandler = GenerateConstructorHandler()
}

class GenerateConstructorHandler : BaseGenerateHandler() {
    override val dialogTitle: String = "Select constructor parameters"

    override fun isImplBlockValid(impl: RsImplItem): Boolean = super.isImplBlockValid(impl) &&
        impl.isSuitableForConstructor

    override fun allowEmptyFields(): Boolean = true

    override fun performRefactoring(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    ) {
        checkWriteAccessAllowed()
        val project = editor.project ?: return
        val structName = struct.name ?: return
        val psiFactory = RsPsiFactory(project)
        val impl = getOrCreateImplBlock(implBlock, psiFactory, structName, struct)

        val anchor = impl.lastChild.lastChild
        val constructor = createConstructor(struct, chosenFields, psiFactory, substitution)
        impl.lastChild.addBefore(constructor, anchor)
        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }

    private fun createConstructor(
        structItem: RsStructItem,
        selectedFields: List<StructMember>,
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
        selectedFields: List<StructMember>,
        substitution: Substitution
    ): String {
        val prefix = if (structItem.isTupleStruct) "(" else "{"
        val postfix = if (structItem.isTupleStruct) ")" else "}"
        val arguments = StructMember.fromStruct(structItem, substitution).joinToString(prefix = prefix, postfix = postfix, separator = ",") {
            if (it !in selectedFields) it.fieldIdentifier else it.argumentIdentifier
        }
        return structItem.nameIdentifier?.text + arguments
    }
}

private val RsImplItem.isSuitableForConstructor: Boolean
    get() = this.traitRef == null && this.members?.childrenOfType<RsFunction>()?.find { it.name == "new" } == null
