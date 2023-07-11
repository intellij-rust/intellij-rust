/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix.editable

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.util.text.StringUtil
import org.jdom.Element
import org.rust.RsBundle
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import java.util.*

class RsPostfixTemplateExpressionCondition(private val expressionType: Type, private val userEnteredTypeName: String = "") : PostfixTemplateExpressionCondition<RsExpr> {
    enum class Type {
        Ref, Slice, Bool, Number, ADT, Array, Tuple, Unit, UserEntered;

        val id: String get() = toString()
    }

    override fun value(element: RsExpr): Boolean {
        return when (expressionType) {
            Type.Ref -> element.type is TyReference
            Type.Slice -> element.type.stripReferences() is TySlice
            Type.Bool -> element.type.stripReferences() is TyBool
            Type.Number -> element.type.stripReferences() is TyNumeric
            Type.ADT -> element.type.stripReferences() is TyAdt
            Type.Array -> element.type.stripReferences() is TyArray
            Type.Tuple -> element.type.stripReferences() is TyTuple
            Type.Unit -> element.type.stripReferences() is TyUnit
            Type.UserEntered -> isUserEnteredType(element)
        }
    }

    private fun CharSequence.withoutTypeParameters(): String {
        val sb = StringBuilder(this.length)
        var depth = 0
        for (ch in this) {
            when {
                ch == '<' -> depth++
                ch == '>' -> depth--
                depth == 0 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isUserEnteredType(element: RsExpr): Boolean {
        val typePathWithoutParams = userEnteredTypeName.withoutTypeParameters()
        val typePath = typePathWithoutParams.substringBeforeLast("::", "")
        val typeName = typePathWithoutParams.substringAfterLast("::").filter { !it.isWhitespace() }

        return if (element.type.stripReferences() is TyAdt) {
            val adtItem = (element.type.stripReferences() as TyAdt).item
            val adtFullPath = adtItem.containingCrate.presentableName + adtItem.crateRelativePath

            val useFullPath = typePath.isNotEmpty()
            if (useFullPath)
                "$typePath::$typeName" == adtFullPath
            else
                typeName == adtItem.name
        } else {
            typeName == element.type.renderInsertionSafe(includeTypeArguments = false).filter { !it.isWhitespace() }
        }
    }

    override fun getPresentableName(): String {
        return when (expressionType) {
            Type.UserEntered -> RsBundle.message("type.0", userEnteredTypeName)
            else -> expressionType.id
        }
    }

    override fun getId(): String = expressionType.id
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RsPostfixTemplateExpressionCondition
        return expressionType == other.expressionType && userEnteredTypeName == other.userEnteredTypeName
    }

    override fun hashCode(): Int = Objects.hash(id, userEnteredTypeName)

    override fun serializeTo(element: Element) {
        super.serializeTo(element) // serialize ID_ATTR
        element.setAttribute(USER_ENTERED_TYPE_NAME_ATTRIBUTE, userEnteredTypeName)
    }

    companion object {
        const val USER_ENTERED_TYPE_NAME_ATTRIBUTE = "Aetna"

        // deserialize expression type
        fun readExternal(condition: Element): RsPostfixTemplateExpressionCondition? {
            val id = condition.getAttributeValue(PostfixTemplateExpressionCondition.ID_ATTR)
            val externalType = Type.values().find { id == it.id } ?: return null

            if (externalType == Type.UserEntered) {
                val userTypeName = condition.getAttributeValue(USER_ENTERED_TYPE_NAME_ATTRIBUTE)
                if (StringUtil.isNotEmpty(userTypeName))
                    return RsPostfixTemplateExpressionCondition(externalType, userTypeName)
            } else
                return RsPostfixTemplateExpressionCondition(externalType)

            return null
        }
    }
}
