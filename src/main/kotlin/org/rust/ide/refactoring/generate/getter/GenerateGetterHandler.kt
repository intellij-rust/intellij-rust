/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate.getter


import com.intellij.openapi.editor.Editor
import org.rust.ide.refactoring.generate.BaseGenerateAction
import org.rust.ide.refactoring.generate.BaseGenerateHandler
import org.rust.ide.refactoring.generate.StructMember
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
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

class GenerateGetterHandler : BaseGenerateHandler() {
    override val dialogTitle: String = "Select Fields to Generate Getters"

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

        chosenFields.forEach {
            val fieldName = it.argumentIdentifier
            val fieldType = it.field.typeReference?.type?.substitute(substitution) ?: TyUnit

            val (borrow, type) = getBorrowAndType(fieldType)
            val fnSignature = "pub fn $fieldName(&self) -> $borrow$type"
            val fnBody = "${borrow}self.$fieldName"

            val accessor = RsPsiFactory(project).createTraitMethodMember("$fnSignature {\n$fnBody\n}")
            impl.members?.addBefore(accessor, impl.members?.rbrace)
        }
    }

    override fun isStructValid(struct: RsStructItem): Boolean {
        if (struct.isTupleStruct) return false
        if (struct.blockFields?.namedFieldDeclList?.isEmpty() != false) return false
        return true
    }

    override fun isFieldValid(member: StructMember, impl: RsImplItem?): Boolean {
        if (member.field.visibility == RsVisibility.Public) return false
        return impl?.expandedMembers?.all { it.name != member.argumentIdentifier } ?: true
    }
}

private fun getBorrowAndType(type: Ty): Pair<String, Ty> {
    return when (type) {
        is TyReference -> Pair("&", type.referenced)
        is TyPrimitive -> Pair("", type)
        is TyAdt -> {
            val item = type.item
            when {
                item == item.knownItems.String -> Pair("&", TyStr)
                item.implLookup.isCopy(type) -> Pair("", type)
                else -> Pair("&", type)
            }
        }
        else -> Pair("&", type)
    }
}
