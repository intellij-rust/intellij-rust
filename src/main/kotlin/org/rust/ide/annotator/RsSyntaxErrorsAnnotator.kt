/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder


class RsSyntaxErrorsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RsFunction -> checkFunction(holder, element)
            is RsStructItem -> checkStructItem(holder, element)
            is RsTypeAlias -> checkTypeAlias(holder, element)
            is RsConstant -> checkConstant(holder, element)
            is RsValueParameterList -> checkValueParameterList(holder, element)
            is RsValueParameter -> checkValueParameter(holder, element)
            is RsTypeParameterList -> checkTypeParameterList(holder, element)
        }
    }
}

private fun checkFunction(holder: AnnotationHolder, fn: RsFunction) {
    when (fn.owner) {
        is RsFunctionOwner.Free -> {
            require(fn.block, holder, "${fn.title} must have a body", fn.lastChild)
            deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
        }
        is RsFunctionOwner.Trait -> {
            deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
            deny(fn.vis, holder, "${fn.title} cannot have the `pub` qualifier")
            fn.const?.let { RsDiagnostic.ConstTraitFnError(it).addToHolder(holder) }
        }
        is RsFunctionOwner.Impl -> {
            require(fn.block, holder, "${fn.title} must have a body", fn.lastChild)
            if (fn.default != null) {
                deny(fn.vis, holder, "Default ${fn.title.firstLower} cannot have the `pub` qualifier")
            }
        }
        is RsFunctionOwner.Foreign -> {
            deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
            deny(fn.block, holder, "${fn.title} cannot have a body")
            deny(fn.const, holder, "${fn.title} cannot have the `const` qualifier")
            deny(fn.unsafe, holder, "${fn.title} cannot have the `unsafe` qualifier")
            deny(fn.externAbi, holder, "${fn.title} cannot have an extern ABI")
        }
    }
}

private fun checkStructItem(holder: AnnotationHolder, struct: RsStructItem) {
    if (struct.kind == RsStructKind.UNION && struct.tupleFields != null) {
        deny(struct.tupleFields, holder, "Union cannot be tuple-like")
    }
}

private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
    val title = "Type `${ta.identifier.text}`"
    val owner = ta.owner
    when (owner) {
        is RsTypeAliasOwner.Free -> {
            deny(ta.default, holder, "$title cannot have the `default` qualifier")
            deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
            require(ta.typeReference, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
        }
        is RsTypeAliasOwner.Trait -> {
            deny(ta.default, holder, "$title cannot have the `default` qualifier")
            deny(ta.vis, holder, "$title cannot have the `pub` qualifier")
            deny(ta.typeParameterList, holder, "$title cannot have generic parameters")
            deny(ta.whereClause, holder, "$title cannot have `where` clause")
        }
        is RsTypeAliasOwner.Impl -> {
            if (owner.impl.`for` == null) {
                RsDiagnostic.AssociatedTypeInInherentImplError(ta).addToHolder(holder)
            } else {
                deny(ta.typeParameterList, holder, "$title cannot have generic parameters")
                deny(ta.whereClause, holder, "$title cannot have `where` clause")
                deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                require(ta.typeReference, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
            }
        }
    }
}

private fun checkConstant(holder: AnnotationHolder, const: RsConstant) {
    val title = if (const.static != null) "Static constant `${const.identifier.text}`" else "Constant `${const.identifier.text}`"
    when (const.owner) {
        is RsConstantOwner.Free -> {
            deny(const.default, holder, "$title cannot have the `default` qualifier")
            require(const.expr, holder, "$title must have a value", const)
        }
        is RsConstantOwner.Foreign -> {
            deny(const.default, holder, "$title cannot have the `default` qualifier")
            require(const.static, holder, "Only static constants are allowed in extern blocks", const.const)
            deny(const.expr, holder, "Static constants in extern blocks cannot have values", const.eq, const.expr)
        }
        is RsConstantOwner.Trait -> {
            deny(const.vis, holder, "$title cannot have the `pub` qualifier")
            deny(const.default, holder, "$title cannot have the `default` qualifier")
            deny(const.static, holder, "Static constants are not allowed in traits")
        }
        is RsConstantOwner.Impl -> {
            deny(const.static, holder, "Static constants are not allowed in impl blocks")
            require(const.expr, holder, "$title must have a value", const)
        }
    }
}

private fun checkValueParameterList(holder: AnnotationHolder, params: RsValueParameterList) {
    val fn = params.parent as? RsFunction ?: return
    when (fn.owner) {
        is RsFunctionOwner.Free -> {
            deny(params.selfParameter, holder, "${fn.title} cannot have `self` parameter")
            deny(params.dotdotdot, holder, "${fn.title} cannot be variadic")
        }
        is RsFunctionOwner.Trait, is RsFunctionOwner.Impl -> {
            deny(params.dotdotdot, holder, "${fn.title} cannot be variadic")
        }
        RsFunctionOwner.Foreign -> {
            deny(params.selfParameter, holder, "${fn.title} cannot have `self` parameter")
            checkDot3Parameter(holder, params.dotdotdot)
        }
    }
}

private fun checkDot3Parameter(holder: AnnotationHolder, dot3: PsiElement?) {
    if (dot3 == null) return
    dot3.rightVisibleLeaves
        .first {
            if (it.text != ")") {
                holder.createErrorAnnotation(it, "`...` must be last in argument list for variadic function")
            }
            return
        }
}

private fun checkValueParameter(holder: AnnotationHolder, param: RsValueParameter) {
    val fn = param.parent.parent as? RsFunction ?: return
    when (fn.owner) {
        is RsFunctionOwner.Free,
        is RsFunctionOwner.Impl,
        is RsFunctionOwner.Foreign -> {
            require(param.pat, holder, "${fn.title} cannot have anonymous parameters", param)
        }
        is RsFunctionOwner.Trait -> {
            denyType<RsPatTup>(param.pat, holder, "${fn.title} cannot have tuple parameters", param)
            if (param.pat == null) {
                val annotation = holder
                    .createWarningAnnotation(param, "Anonymous functions parameters are deprecated (RFC 1685)")

                val fix = SubstituteTextFix.replace(
                    "Add dummy parameter name",
                    param.containingFile,
                    param.textRange,
                    "_: ${param.text}"
                )
                val descriptor = InspectionManager.getInstance(param.project)
                    .createProblemDescriptor(param, annotation.message, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true)

                annotation.registerFix(fix, null, null, descriptor)
            }
        }
    }
}

private fun checkTypeParameterList(holder: AnnotationHolder, element: RsTypeParameterList) {
    val lifetimeParams = element.lifetimeParameterList
    if (lifetimeParams.isEmpty()) return
    val startOfTypeParams = element.typeParameterList.firstOrNull()?.textOffset ?: return
    for (e in lifetimeParams) {
        if (e.textOffset > startOfTypeParams) {
            holder.createErrorAnnotation(e, "Lifetime parameters must be declared prior to type parameters")
        }
    }
}

private fun require(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
    if (el != null) null
    else holder.createErrorAnnotation(highlightElements.combinedRange ?: TextRange.EMPTY_RANGE, message)

private fun deny(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
    if (el == null) null
    else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

private inline fun <reified T : RsElement> denyType(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
    if (el !is T) null
    else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

private val Array<out PsiElement?>.combinedRange: TextRange?
    get() = if (isEmpty())
        null
    else filterNotNull()
        .map { it.textRange }
        .reduce(TextRange::union)

private val PsiElement.rightVisibleLeaves: Sequence<PsiElement>
    get() = generateSequence(PsiTreeUtil.nextVisibleLeaf(this), { el -> PsiTreeUtil.nextVisibleLeaf(el) })

private val String.firstLower: String
    get() = if (isEmpty()) this else this[0].toLowerCase() + substring(1)

