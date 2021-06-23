/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate.setter


import com.intellij.openapi.editor.Editor
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.generate.BaseGenerateAction
import org.rust.ide.refactoring.generate.BaseGenerateHandler
import org.rust.ide.refactoring.generate.GenerateAccessorHandler
import org.rust.ide.refactoring.generate.StructMember
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessAllowed

class GenerateSetterAction : BaseGenerateAction() {
    override val handler: BaseGenerateHandler = GenerateSetterHandler()
}

class GenerateSetterHandler : GenerateAccessorHandler() {
    override val dialogTitle: String = "Select Fields to Generate Setters"

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
            val typeStr = typeRef.substAndGetText(substitution)

            val fnSignature = "pub fn ${methodName(it)}(&mut self, $fieldName: $typeStr)"
            val fnBody = "self.$fieldName = $fieldName;"

            val accessor = RsPsiFactory(project).createTraitMethodMember("$fnSignature {\n$fnBody\n}")
            impl.members?.addBefore(accessor, impl.members?.rbrace) as RsFunction
        }
    }

    override fun methodName(member: StructMember): String = "set_${member.argumentIdentifier}"
}
