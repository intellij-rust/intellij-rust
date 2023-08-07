/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.util.containers.map2Array
import org.rust.ide.hints.parameter.RsStructLiteralParameterInfoHandler.Description
import org.rust.ide.utils.findElementAtIgnoreWhitespaceBefore
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.RsStructLiteralBody
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.substAndGetText
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type
import org.rust.stdext.mapNotNullToSet

class RsStructLiteralParameterInfoHandler : RsAsyncParameterInfoHandler<RsStructLiteralBody, Description>() {

    class Description(val fields: Array<Field>)
    class Field(val name: String, val type: String)

    override fun findTargetElement(file: PsiFile, offset: Int): RsStructLiteralBody? =
        file.findElementAt(offset)?.ancestorStrict()

    override fun calculateParameterInfo(element: RsStructLiteralBody): Array<Description>? {
        val structLiteral = element.parent as? RsStructLiteral ?: return null
        val struct = structLiteral.path.reference?.deepResolve() as? RsFieldsOwner ?: return null
        if (struct.blockFields == null) return null
        val subst = (structLiteral.type as? TyAdt)?.typeParameterValues ?: emptySubstitution
        val fields = struct.namedFields.map2Array {
            val name = it.name ?: ""
            val type = it.typeReference?.substAndGetText(subst) ?: "_"
            Field(name, type)
        }
        return arrayOf(Description(fields))
    }

    override fun updateParameterInfo(parameterOwner: RsStructLiteralBody, context: UpdateParameterInfoContext) {
        val description = context.objectsToView.singleOrNull() as? Description ?: return
        val declaredFields = description.fields.map { it.name }

        val fields = parameterOwner.structLiteralFieldList.mapNotNullToSet { it.referenceName }
        val currentField = findCurrentFieldName(parameterOwner, context.offset)

        val index = when {
            currentField != null -> declaredFields.indexOf(currentField)
            declaredFields.size == fields.size -> 0
            else -> declaredFields.indexOfFirst { it !in fields }
        }
        context.setCurrentParameter(index)
    }

    private fun findCurrentFieldName(structLiteral: RsStructLiteralBody, offset: Int): String? {
        val file = structLiteral.containingFile
        val element1 = file.findElementAtIgnoreWhitespaceBefore(offset)
        val element2 = element1?.getPrevNonWhitespaceSibling()
        val field = element1?.ancestorOrSelf<RsStructLiteralField>()
            ?: element2?.ancestorOrSelf<RsStructLiteralField>()
            ?: return null
        return field.referenceName
    }

    override fun updateUI(p: Description, context: ParameterInfoUIContext) {
        val fields = p.fields
        val fieldsText = fields.map2Array { "${it.name}: ${it.type}" }
        val text = fieldsText.joinToString(", ").ifEmpty { "<no fields>" }
        val range = getArgumentRange(fieldsText, context.currentParameterIndex)
        updateUI(text, range, context)
    }
}
