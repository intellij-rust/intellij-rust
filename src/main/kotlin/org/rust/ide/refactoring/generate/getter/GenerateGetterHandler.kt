/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate.getter


import com.intellij.openapi.editor.Editor
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.generate.BaseGenerateAction
import org.rust.ide.refactoring.generate.BaseGenerateHandler
import org.rust.ide.refactoring.generate.GenerateAccessorHandler
import org.rust.ide.refactoring.generate.StructMember
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsVisibility
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.psi.ext.isTupleStruct
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

        return chosenFields.map {
            val fieldName = it.argumentIdentifier
            val fieldType = it.field.typeReference?.type?.substitute(substitution) ?: TyUnit

            val (borrow, type) = getBorrowAndType(fieldType, it.field)
            val typeStr = type.renderInsertionSafe(useAliasNames = true, includeLifetimeArguments = true)

            val fnSignature = "pub fn $fieldName(&self) -> $borrow$typeStr"
            val fnBody = "${borrow}self.$fieldName"

            val accessor = RsPsiFactory(project).createTraitMethodMember("$fnSignature {\n$fnBody\n}")
            impl.members?.addBefore(accessor, impl.members?.rbrace) as RsFunction
        }
    }

    override fun methodName(member: StructMember): String = member.argumentIdentifier
}

private fun getBorrowAndType(type: Ty, context: RsElement): Pair<String, Ty> {
    return when {
        type is TyPrimitive -> Pair("", type)
        type is TyAdt -> {
            val item = type.item
            when {
                item == item.knownItems.String -> Pair("&", TyStr)
                !type.isMovesByDefault(context.implLookup) -> Pair("", type)
                else -> Pair("&", type)
            }
        }
        !type.isMovesByDefault(context.implLookup) -> Pair("", type)
        else -> Pair("&", type)
    }
}
