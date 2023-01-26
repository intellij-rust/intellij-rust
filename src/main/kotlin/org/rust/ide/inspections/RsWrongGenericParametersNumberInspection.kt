/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.util.text.StringUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0049 error.
 */
class RsWrongGenericParametersNumberInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitFunction2(function: RsFunction) {
            checkParameters(holder, function, "type") { typeParameters }
            checkParameters(holder, function, "const") { constParameters }
        }

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitTypeAlias2(alias: RsTypeAlias) {
            checkParameters(holder, alias, "type") { typeParameters }
            checkParameters(holder, alias, "const") { constParameters }
        }
    }

    private fun <T> checkParameters(
        holder: RsProblemsHolder,
        item: T,
        paramType: String,
        getParameters: RsGenericDeclaration.() -> List<RsGenericParameter>
    ) where T : RsAbstractable, T : RsGenericDeclaration {
        val itemName = item.name ?: return
        val itemType = when (item) {
            is RsFunction -> "Method"
            is RsTypeAlias -> "Type"
            else -> return
        }
        val superItem = item.superItem as? RsGenericDeclaration ?: return
        val toHighlight = item.typeParameterList ?: item.nameIdentifier ?: return

        val typeParameters = item.getParameters()
        val superTypeParameters = superItem.getParameters()
        if (typeParameters.size == superTypeParameters.size) return

        val paramName = "$paramType ${StringUtil.pluralize("parameter", typeParameters.size)}"
        val superParamName = "$paramType ${StringUtil.pluralize("parameter", superTypeParameters.size)}"
        val problemText = "$itemType `$itemName` has ${typeParameters.size} $paramName " +
            "but its trait declaration has ${superTypeParameters.size} $superParamName"
        RsDiagnostic.WrongNumberOfGenericParameters(toHighlight, problemText).addToHolder(holder)
    }
}
