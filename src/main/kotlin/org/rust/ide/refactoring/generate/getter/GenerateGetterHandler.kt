/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate.getter


import com.intellij.openapi.editor.Editor
import org.rust.ide.refactoring.generate.BaseGenerateAction
import org.rust.ide.refactoring.generate.BaseGenerateHandler
import org.rust.ide.refactoring.generate.GenerateAccessorHandler
import org.rust.ide.refactoring.generate.StructMember
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessAllowed

class GenerateGetterAction : BaseGenerateAction() {
    override val handler: BaseGenerateHandler = GenerateGetterHandler()
}

class GenerateGetterHandler : GenerateAccessorHandler() {
    override val dialogTitle: String = "Select Fields to Generate Getters"

    override fun generateAccessors(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    ): List<RsFunction>? {
        checkWriteAccessAllowed()
        val project = editor.project ?: return null
        val structName = struct.name ?: return null
        val psiFactory = RsPsiFactory(project)
        val impl = getOrCreateImplBlock(implBlock, psiFactory, structName, struct)

        return chosenFields.mapNotNull {
            val fieldName = it.argumentIdentifier
            val typeRef = it.field.typeReference ?: return@mapNotNull null
            val fieldType = typeRef.type.substitute(substitution)

            val (borrow, typeStr) = getBorrowAndType(fieldType, it.typeReferenceText, it.field)
            val fnSignature = "pub fn $fieldName(&self) -> $borrow$typeStr"
            val fnBody = "${borrow}self.$fieldName"

            val accessor = RsPsiFactory(project).createTraitMethodMember("$fnSignature {\n$fnBody\n}")
            impl.members?.addBefore(accessor, impl.members?.rbrace) as RsFunction
        }
    }

    override fun methodName(member: StructMember): String = member.argumentIdentifier
}

private fun getBorrowAndType(
    type: Ty,
    typeReferenceText: String,
    context: RsElement
): Pair<String, String> {
    return when {
        type is TyPrimitive -> "" to typeReferenceText
        type is TyAdt -> {
            val item = type.item
            when {
                item == item.knownItems.String -> {
                    "&" to "str"
                }
                !type.isMovesByDefault(context.implLookup) -> "" to typeReferenceText
                else -> "&" to typeReferenceText
            }
        }
        !type.isMovesByDefault(context.implLookup) -> "" to typeReferenceText
        else -> "&" to typeReferenceText
    }
}
