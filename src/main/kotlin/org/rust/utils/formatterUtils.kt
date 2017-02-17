package org.rust.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*

fun RsNamedElement.getFormattedParts(): Pair<String, String> {
    // Example:
    // fn item looks like this:
    // ```
    //     ///doc comment
    //     #[attribute]
    //     pub const unsafe extern "C" fn foo<T>(x: T): where T: Clone { ... }
    // ```
    //
    // we want to show only the signature, and make the name bold

    var signatureStartElement = firstChild
    loop@ while (true) {
        when (signatureStartElement) {
            is PsiWhiteSpace, is PsiComment, is RsOuterAttr -> {
                signatureStartElement = signatureStartElement.nextSibling
            }
            else -> break@loop
        }
    }

    val signatureStart = signatureStartElement?.startOffsetInParent ?: 0
    val stopAt = when (this) {
        is RsFunction -> listOf(whereClause, retType, valueParameterList)
        is RsStructItem -> if (blockFields != null) listOf(whereClause) else listOf(whereClause, tupleFields)
        is RsEnumItem -> listOf(whereClause)
        is RsEnumVariant -> listOf(tupleFields)
        is RsTraitItem -> listOf(whereClause)
        is RsTypeAlias -> listOf(typeReference, typeParamBounds, whereClause)
        is RsConstant -> listOf(expr, typeReference)
        is RsSelfParameter -> listOf(typeReference)
        else -> listOf(navigationElement)
    }

    // pick (in order) elements we should stop at
    // if they all fail, drop down to the end of the id element
    val idElement = if (this is RsSelfParameter) self else navigationElement
    val signatureEnd = stopAt
        .filterNotNull().firstOrNull()
        ?.let { it.startOffsetInParent + it.textLength }
        ?: idElement.startOffsetInParent + idElement.textLength

    val identStart = idElement.startOffsetInParent
    val identEnd = identStart + idElement.textLength
    check(signatureStart <= identStart && identStart <= identEnd &&
        identEnd <= signatureEnd && signatureEnd <= textLength)

    val beforeIdent = text.substring(signatureStart, identStart)
    val afterIdent = text.substring(identEnd, signatureEnd)
        .replace("""\s+""".toRegex(), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .replace(" ,", ",")
        .trimEnd()

    return Pair(beforeIdent, afterIdent)
}
