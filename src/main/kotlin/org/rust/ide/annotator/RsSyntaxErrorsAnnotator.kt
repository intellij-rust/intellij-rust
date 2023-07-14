/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.text.SemVer
import org.rust.RsBundle
import org.rust.cargo.util.parseSemVer
import org.rust.ide.fixes.*
import org.rust.ide.refactoring.RsNamesValidator.Companion.RESERVED_KEYWORDS
import org.rust.lang.core.CompilerFeature.Companion.C_VARIADIC
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.openapiext.forEachChild
import org.rust.stdext.capitalized
import org.rust.stdext.pluralize
import java.lang.Integer.max

class RsSyntaxErrorsAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RsBreakExpr -> checkBreakExpr(holder, element)
            is RsContExpr -> {
                checkLabelInWhileCondition(holder, element)
                checkLabelPointingToBlock(holder, element)
            }
            is RsExternAbi -> checkExternAbi(holder, element)
            is RsItemElement -> {
                checkItem(holder, element)
                when (element) {
                    is RsFunction -> checkFunction(holder, element)
                    is RsStructItem -> checkStructItem(holder, element)
                    is RsTypeAlias -> checkTypeAlias(holder, element)
                    is RsImplItem -> checkImplItem(holder, element)
                    is RsConstant -> checkConstant(holder, element)
                    is RsModItem -> checkModItem(holder, element)
                    is RsModDeclItem -> checkModDeclItem(holder, element)
                    is RsForeignModItem -> checkForeignModItem(holder, element)
                }
            }
            is RsMacro -> checkMacro(holder, element)
            is RsMacroCall -> checkMacroCall(holder, element)
            is RsValueParameterList -> checkValueParameterList(holder, element)
            is RsValueParameter -> checkValueParameter(holder, element)
            is RsTypeParameterList -> checkTypeParameterList(holder, element)
            is RsTypeParameter -> checkTypeParameter(holder, element)
            is RsTypeArgumentList -> checkTypeArgumentList(holder, element)
            is RsLetExpr -> checkLetExpr(holder, element)
            is RsPat -> checkPat(holder, element)
            is RsTraitType -> checkTraitType(holder, element)
            is RsUnderscoreExpr -> checkUnderscoreExpr(holder, element)
            is RsWherePred -> checkWherePred(holder, element)
            is RsLambdaExpr -> checkLambdaExpr(holder, element)
            is RsDefaultParameterValue -> checkDefaultParameterValue(holder, element)
            is RsTypeParamBounds -> checkTypeParamBounds(holder, element)
            is RsSuperStructs -> checkSuperStructs(holder, element)
            is RsPrefixIncExpr, is RsPostfixIncExpr, is RsPostfixDecExpr -> checkIncDecOp(holder, element as RsExpr)
            else -> {
                checkReservedKeyword(holder, element)
            }
        }
    }

}

private fun checkTypeParameter(holder: AnnotationHolder, item: RsTypeParameter) {
    if (item.bounds.count { it.hasQ } > 1) {
        RsDiagnostic.MultipleRelaxedBoundsError(item).addToHolder(holder)
    }
}

private fun checkImplItem(holder: AnnotationHolder, item: RsImplItem) {
    val unsafe = item.unsafe
    if (unsafe != null && item.traitRef == null) {
        val typeReference = item.typeReference ?: return
        RsDiagnostic.UnsafeInherentImplError(
            typeReference, listOf(RemoveElementFix(unsafe))
        ).addToHolder(holder)
    }
}

private fun checkBreakExpr(holder: AnnotationHolder, item: RsBreakExpr) {
    checkLabelInWhileCondition(holder, item)
    item.expr ?: return
    val label = item.label
    val loop = if (label != null) {
        // Do return, because an error code E0426 was raised if the definition of the label was not found
        val labelBlock = label.reference.resolve() ?: return
        labelBlock.parent
    } else {
        // Use RsItemElement::class as a separator
        item.parentOfTypes(RsForExpr::class, RsWhileExpr::class, RsLoopExpr::class, RsItemElement::class)
    }
    when (loop) {
        is RsForExpr -> RsDiagnostic.BreakExprInNonLoopError(item, "for").addToHolder(holder)
        is RsWhileExpr -> RsDiagnostic.BreakExprInNonLoopError(item, "while").addToHolder(holder)
    }
}

private fun checkLabelInWhileCondition(holder: AnnotationHolder, item: RsLabelReferenceOwner) {
    if (item.label != null) return
    val condition = PsiTreeUtil.getParentOfType(
        item,
        RsCondition::class.java,
        true,
        RsLooplikeExpr::class.java,
        RsItemElement::class.java
    ) ?: return
    if (condition.parent is RsWhileExpr) {
        val fixes = if (!holder.isBatchMode) listOf(RsAddLabelFix(item)) else emptyList()
        RsDiagnostic.BreakContinueInWhileConditionWithoutLoopError(item, item.text, fixes).addToHolder(holder)
    }
}

private fun checkLabelPointingToBlock(holder: AnnotationHolder, contExpr: RsContExpr) {
    val blocks = contExpr.label?.reference?.multiResolve()
        ?.map { it.parent }
        ?.filterIsInstance<RsBlockExpr>() ?: emptyList()
    if (blocks.isNotEmpty()) {
        val fixes = if (blocks.size == 1) listOf(RsConvertBlockToLoopFix(blocks[0])) else emptyList()
        RsDiagnostic.ContinueLabelTargetBlock(contExpr, fixes).addToHolder(holder)
    }
}

private fun checkItem(holder: AnnotationHolder, item: RsItemElement) {
    checkItemOrMacro(item, item.itemKindName.pluralize().capitalized(), item.itemDefKeyword, holder)
}

private fun checkMacro(holder: AnnotationHolder, element: RsMacro) =
    checkItemOrMacro(element, "Macros", element.macroRules, holder)

private fun checkItemOrMacro(item: RsElement, itemName: String, highlightElement: PsiElement, holder: AnnotationHolder) {
    if (item !is RsAbstractable) {
        val parent = item.context
        val owner = if (parent is RsMembers) parent.context else parent
        if (owner is RsItemElement && (owner is RsForeignModItem || owner is RsTraitOrImpl)) {
            holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.are.not.allowed.inside", itemName, owner.article, owner.itemKindName))
                .range(highlightElement).create()
        }
    }

    if (item !is RsAbstractable && item !is RsTraitOrImpl) {
        denyDefaultKeyword(item, holder, itemName)
    }
}

private fun denyDefaultKeyword(item: RsElement, holder: AnnotationHolder, itemName: String) {
    deny(
        item.node.findChildByType(RsElementTypes.DEFAULT)?.psi,
        holder,
        RsBundle.message("inspection.message.cannot.have.default.qualifier11", itemName)
    )
}

private fun checkMacroCall(holder: AnnotationHolder, element: RsMacroCall) {
    denyDefaultKeyword(element, holder, "Macro invocations")
}

private fun checkFunction(holder: AnnotationHolder, fn: RsFunction) {
    checkCanUseVariadic(holder, fn)
    if (fn.isMain && fn.getGenericParameters().isNotEmpty()) {
        val typeParameterList = fn.typeParameterList ?: fn
        RsDiagnostic.MainWithGenericsError(
            typeParameterList, listOf(
            RemoveElementFix(
                typeParameterList
            )
        )
        ).addToHolder(holder)
    }
    when (fn.owner) {
        is RsAbstractableOwner.Free -> {
            require(fn.block, holder, RsBundle.message("inspection.message.must.have.body2", fn.title), fn.lastChild)
            deny(fn.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier10", fn.title))
        }
        is RsAbstractableOwner.Trait -> {
            deny(fn.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier9", fn.title))
            deny(fn.vis, holder, RsBundle.message("inspection.message.cannot.have.pub.qualifier2", fn.title))
            fn.const?.let { RsDiagnostic.ConstTraitFnError(it).addToHolder(holder) }
        }
        is RsAbstractableOwner.Impl -> {
            require(fn.block, holder, RsBundle.message("inspection.message.must.have.body", fn.title), fn.lastChild)
            if (fn.default != null) {
                deny(fn.vis, holder, RsBundle.message("inspection.message.default.cannot.have.pub.qualifier", fn.title.firstLower))
            }
        }
        is RsAbstractableOwner.Foreign -> {
            deny(fn.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier8", fn.title))
            deny(fn.block, holder, RsBundle.message("inspection.message.cannot.have.body2", fn.title))
            deny(fn.const, holder, RsBundle.message("inspection.message.cannot.have.const.qualifier", fn.title))
            deny(fn.unsafe, holder, RsBundle.message("inspection.message.cannot.have.unsafe.qualifier", fn.title))
            deny(fn.externAbi, holder, RsBundle.message("inspection.message.cannot.have.extern.abi", fn.title))
        }
    }
}

private fun checkStructItem(holder: AnnotationHolder, struct: RsStructItem) {
    if (struct.kind == RsStructKind.UNION && struct.tupleFields != null) {
        deny(struct.tupleFields, holder, RsBundle.message("inspection.message.union.cannot.be.tuple.like"))
    }
}

private fun checkSuperStructs(holder: AnnotationHolder, element: RsSuperStructs) {
    deny(
        element,
        holder,
        RsBundle.message("error.message.struct.inheritance.is.not.supported"),
        *element.typeReferenceList.toTypedArray(),
        fix = RemoveElementFix(element, "super structs")
    )
}

private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
    val title = RsBundle.message("inspection.message.type.0", ta.identifier.text)

    val eq = ta.eq
    val whereClauseBeforeEq = ta.whereClauseList.firstOrNull()?.takeIf { eq != null && it.startOffset < eq.startOffset }
    val whereClauseAfterEq = ta.whereClauseList.lastOrNull()?.takeIf { eq != null && it.startOffset > eq.startOffset }

    when (val owner = ta.owner) {
        is RsAbstractableOwner.Free -> {
            deny(ta.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier7", title))
            deny(ta.typeParamBounds, holder, RsBundle.message("inspection.message.bounds.on.have.no.effect3", title))
            require(ta.typeReference, holder, RsBundle.message("inspection.message.should.have.body2", title), ta)
            deny(whereClauseAfterEq, holder, RsBundle.message("inspection.message.cannot.have.where.clause.after.type", title))
        }
        is RsAbstractableOwner.Trait -> {
            deny(ta.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier6", title))
        }
        is RsAbstractableOwner.Impl -> {
            if (owner.isInherent) {
                deny(ta.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier5", title))
            }
            deny(ta.typeParamBounds, holder, RsBundle.message("inspection.message.bounds.on.have.no.effect2", title))
            require(ta.typeReference, holder, RsBundle.message("inspection.message.should.have.body", title), ta)

            val version = ta.cargoProject?.rustcInfo?.version?.semver ?: return
            if (version < DEPRECATED_WHERE_CLAUSE_LOCATION_VERSION) return
            deny(whereClauseBeforeEq, holder, RsBundle.message("inspection.message.cannot.have.where.clause.before.type", title), severity = HighlightSeverity.WEAK_WARNING)
        }
        RsAbstractableOwner.Foreign -> {
            deny(ta.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier4", title))
            deny(ta.typeParameterList, holder, RsBundle.message("inspection.message.cannot.have.generic.parameters", title))
            deny(ta.whereClause, holder, RsBundle.message("inspection.message.cannot.have.where.clause", title))
            deny(ta.typeParamBounds, holder, RsBundle.message("inspection.message.bounds.on.have.no.effect", title))
            deny(ta.typeReference, holder, RsBundle.message("inspection.message.cannot.have.body", title), ta)
        }
    }
}

private val DEPRECATED_WHERE_CLAUSE_LOCATION_VERSION: SemVer = "1.61.0".parseSemVer()

private fun checkConstant(holder: AnnotationHolder, const: RsConstant) {
    val name = const.nameLikeElement.text
    val title = if (const.static != null) RsBundle.message("inspection.message.static.constant", name) else RsBundle.message("inspection.message.constant", name)
    when (const.owner) {
        is RsAbstractableOwner.Free -> {
            deny(const.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier3", title))
            require(const.expr, holder, RsBundle.message("inspection.message.must.have.value2", title), const)
        }
        is RsAbstractableOwner.Foreign -> {
            deny(const.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier2", title))
            require(const.static, holder, RsBundle.message("inspection.message.only.static.constants.are.allowed.in.extern.blocks"), const.const)
            deny(const.expr, holder, RsBundle.message("inspection.message.static.constants.in.extern.blocks.cannot.have.values"), const.eq, const.expr)
        }
        is RsAbstractableOwner.Trait -> {
            deny(const.vis, holder, RsBundle.message("inspection.message.cannot.have.pub.qualifier", title))
            deny(const.default, holder, RsBundle.message("inspection.message.cannot.have.default.qualifier", title))
            deny(const.static, holder, RsBundle.message("inspection.message.static.constants.are.not.allowed.in.traits"))
        }
        is RsAbstractableOwner.Impl -> {
            deny(const.static, holder, RsBundle.message("inspection.message.static.constants.are.not.allowed.in.impl.blocks"))
            require(const.expr, holder, RsBundle.message("inspection.message.must.have.value", title), const)
        }
    }
    checkConstantType(holder, const)
}

private fun checkConstantType(holder: AnnotationHolder, element: RsConstant) {
    if (element.colon == null && element.typeReference == null) {
        val nameElement = element.nameLikeElement
        val typeText = if (element.isConst) {
            "const"
        } else {
            "static"
        }
        val message = RsBundle.message("inspection.message.missing.type.for.item", typeText)

        val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(nameElement)

        val expr = element.expr
        if (expr != null) {
            annotation.withFix(AddTypeFix(nameElement, expr.type))
        }

        annotation.create()
    }
}

private fun checkValueParameterList(holder: AnnotationHolder, params: RsValueParameterList) {
    val fn = params.parent as? RsFunction ?: return
    when (fn.owner) {
        is RsAbstractableOwner.Free -> {
            deny(params.selfParameter, holder, RsBundle.message("inspection.message.cannot.have.self.parameter2", fn.title))
            checkVariadic(holder, fn, params.variadic?.dotdotdot)
        }
        is RsAbstractableOwner.Trait, is RsAbstractableOwner.Impl -> {
            deny(params.variadic?.dotdotdot, holder, RsBundle.message("inspection.message.cannot.be.variadic2", fn.title))
        }
        RsAbstractableOwner.Foreign -> {
            deny(params.selfParameter, holder, RsBundle.message("inspection.message.cannot.have.self.parameter", fn.title))
            checkDot3Parameter(holder, params.variadic?.dotdotdot)
        }
    }
}

private fun checkVariadic(holder: AnnotationHolder, fn: RsFunction, dot3: PsiElement?) {
    if (dot3 == null) return
    if (fn.isUnsafe && fn.actualAbiName == "C") {
        C_VARIADIC.check(holder, dot3, "C-variadic functions")
    } else {
        deny(dot3, holder, RsBundle.message("inspection.message.cannot.be.variadic", fn.title))
    }
}

private fun checkDot3Parameter(holder: AnnotationHolder, dot3: PsiElement?) {
    if (dot3 == null) return
    dot3.rightVisibleLeaves
        .first {
            if (it.text != ")") {
                holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.must.be.last.in.argument.list.for.variadic.function"))
                    .range(it).create()
            }
            return
        }
}

private fun isComplexPattern(pat: RsPat?): Boolean {
    return when (pat) {
        null, is RsPatWild -> false
        is RsPatIdent -> {
            val binding = pat.patBinding
            binding.mutability == Mutability.MUTABLE || binding.kind is RsBindingModeKind.BindByReference || pat.at != null
        }

        is RsPatMacro -> {
            val expansion = pat.macroCall.expansion
            return expansion != null && expansion is MacroExpansion.Pat && isComplexPattern(expansion.pat)
        }

        else -> true
    }
}

private fun checkPat(holder: AnnotationHolder, element: RsPat) {
    if (element is RsPatRange) {
        checkPatRange(holder, element)
    }

    checkGeneralPat(element, holder)
}

private fun checkGeneralPat(element: RsPat, holder: AnnotationHolder) {
    val valueParameter = element.parent
    if (valueParameter !is RsValueParameter) {
        return
    }
    val fn = valueParameter.parent.parent ?: return
    when (fn) {
        is RsFunction -> {
            checkFunctionParameterPatIsSimple(fn, element, holder)
        }

        is RsFnPointerType -> {
            checkFunctionPointerTypeParameterPatIsSimple(element, holder)
        }
    }
}

private fun checkFunctionParameterPatIsSimple(fn: RsFunction, element: RsPat, holder: AnnotationHolder) {
    val isComplexPattern = isComplexPattern(element)
    when (fn.owner) {
        is RsAbstractableOwner.Foreign -> {
            if (isComplexPattern) {
                RsDiagnostic.PatternArgumentInForeignFunctionError(element).addToHolder(holder)
            }
        }

        is RsAbstractableOwner.Trait -> {
            if (isComplexPattern && fn.block == null) {
                RsDiagnostic.PatternArgumentInFunctionWithoutBodyError(element).addToHolder(holder)
            }
        }

        else -> {}
    }
}

private fun checkFunctionPointerTypeParameterPatIsSimple(element: RsPat, holder: AnnotationHolder) {
    val isComplexPattern = isComplexPattern(element)
    if (isComplexPattern || element is RsPatMacro) {
        RsDiagnostic.PatternArgumentInFunctionPointerTypeError(element).addToHolder(holder)
    }
}

private fun checkValueParameter(holder: AnnotationHolder, param: RsValueParameter) {
    val parent = param.parent.parent ?: return
    when (parent) {
        is RsFunction -> {
            checkValueParameterInFunction(parent, param, holder)
        }
    }
}

private fun checkDefaultParameterValue(holder: AnnotationHolder, default: RsDefaultParameterValue) {
    val fix = RemoveElementFix(default, "default parameter value")
    deny(default.expr, holder, RsBundle.message("inspection.message.default.parameter.values.are.not.supported.in.rust"), fix = fix)
}

private fun checkTypeParamBounds(holder: AnnotationHolder, bounds: RsTypeParamBounds) {
    val impl = bounds.impl
    if (impl != null) {
        val fix = RemoveElementFix(impl, "`impl` keyword")
        deny(impl, holder, RsBundle.message("inspection.message.expected.trait.bound.found.impl.trait.type"), fix = fix)
    }

    val dyn = bounds.dyn
    if (dyn != null) {
        val fix = RemoveElementFix(dyn, "`dyn` keyword")
        deny(dyn, holder, RsBundle.message("inspection.message.invalid.dyn.keyword"), fix = fix)
    }
}

private fun checkValueParameterInFunction(fn: RsFunction, param: RsValueParameter, holder: AnnotationHolder) {
    val pat = param.pat
    when (fn.owner) {
        is RsAbstractableOwner.Free,
        is RsAbstractableOwner.Impl -> {
            require(pat, holder, RsBundle.message("inspection.message.cannot.have.anonymous.parameters2", fn.title), param)
        }

        is RsAbstractableOwner.Foreign -> {
            require(pat, holder, RsBundle.message("inspection.message.cannot.have.anonymous.parameters", fn.title), param)
        }

        is RsAbstractableOwner.Trait -> {
            if (pat == null) {
                val message = RsBundle.message("inspection.message.anonymous.functions.parameters.are.deprecated.rfc")
                val annotation = holder.newAnnotation(HighlightSeverity.WARNING, message)

                val fix = SubstituteTextFix.replace(
                    RsBundle.message("intention.name.add.dummy.parameter.name"),
                    param.containingFile,
                    param.textRange,
                    "_: ${param.text}"
                )
                val descriptor = InspectionManager.getInstance(param.project)
                    .createProblemDescriptor(param, message, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true)

                annotation.newLocalQuickFix(fix, descriptor).registerFix().create()
            }
        }
    }
}

private fun checkTypeParameterList(holder: AnnotationHolder, element: RsTypeParameterList) {
    val parent = element.parent
    if (parent is RsImplItem || parent is RsFunction) {
        if ((parent as? RsFunction)?.owner == RsAbstractableOwner.Foreign) {
            val genericsCount = element.typeParameterList.size
            val constCount = element.constParameterList.size
            if (genericsCount > 0 || constCount > 0) {
                val kinds = when {
                    constCount == 0 -> "type"
                    genericsCount == 0 -> "const"
                    else -> "type or const"
                }
                RsDiagnostic.ConstOrTypeParamsInExternError(element, kinds).addToHolder(holder)
            }
        }

        element.typeParameterList
            .mapNotNull { it.typeReference }
            .forEach {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    RsBundle.message("inspection.message.defaults.for.type.parameters.are.only.allowed.in.struct.enum.type.or.trait.definitions")
                ).range(it).create()
            }
    } else {
        val lastNotDefaultIndex = max(element.typeParameterList.indexOfLast { it.typeReference == null }, 0)
        element.typeParameterList
            .take(lastNotDefaultIndex)
            .filter { it.typeReference != null }
            .forEach {
                holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.type.parameters.with.default.must.be.trailing"))
                    .range(it).create()
            }
    }

    checkTypeList(element, "parameters", holder)
}

private fun checkTypeArgumentList(holder: AnnotationHolder, args: RsTypeArgumentList) {
    checkTypeList(args, "arguments", holder)

    val startOfAssocTypeBindings = args.assocTypeBindingList.firstOrNull()?.textOffset ?: return
    for (generic in args.lifetimeList + args.typeReferenceList + args.exprList) {
        if (generic.textOffset > startOfAssocTypeBindings) {
            holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.generic.arguments.must.come.before.first.constraint"))
                .range(generic).create()
        }
    }
}

private fun checkTypeList(typeList: PsiElement, elementsName: String, holder: AnnotationHolder) {
    var kind = TypeKind.LIFETIME
    typeList.forEachChild { child ->
        val newKind = TypeKind.forType(child) ?: return@forEachChild
        if (newKind.canStandAfter(kind)) {
            kind = newKind
        } else {
            val newStateName = newKind.presentableName.capitalized()
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                RsBundle.message("inspection.message.must.be.declared.prior.to", newStateName, elementsName, kind.presentableName, elementsName)
            ).range(child).create()
        }
    }
}

private fun checkExternAbi(holder: AnnotationHolder, element: RsExternAbi) {
    val litExpr = element.litExpr ?: return
    val abyLiteralKind = litExpr.kind ?: return
    if (abyLiteralKind !is RsLiteralKind.String) {
        holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.non.string.abi.literal")).range(litExpr).create()
    }
}

private fun checkModDeclItem(holder: AnnotationHolder, element: RsModDeclItem) {
    checkInvalidUnsafe(holder, element.unsafe, "Module")
}

private fun checkModItem(holder: AnnotationHolder, element: RsModItem) {
    checkInvalidUnsafe(holder, element.unsafe, "Module")
}

private fun checkForeignModItem(holder: AnnotationHolder, element: RsForeignModItem) {
    checkInvalidUnsafe(holder, element.unsafe, "Extern block")
}

private fun checkInvalidUnsafe(holder: AnnotationHolder, unsafe: PsiElement?, itemName: String) {
    if (unsafe != null) {
        holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.cannot.be.declared.unsafe", itemName)).range(unsafe).create()
    }
}

private fun checkLetExpr(holder: AnnotationHolder, element: RsLetExpr) {
    var ancestor = element.parent
    while (when (ancestor) {
        is RsCondition, is RsMatchArmGuard -> return
        is RsBinaryExpr -> ancestor.binaryOp.andand != null
        else -> false
    }) {
        ancestor = ancestor.parent
    }
    deny(element, holder, RsBundle.message("inspection.message.let.expressions.are.not.supported.here"))
}

private fun checkPatRange(holder: AnnotationHolder, element: RsPatRange) {
    val start = element.start
    val end = element.end
    when {
        element.dotdot != null -> when {
            start == null && end == null -> deny(element.dotdot, holder, RsBundle.message("inspection.message.unexpected3"))
        }
        element.dotdoteq != null -> when {
            start == null && end == null -> deny(element.dotdoteq, holder, RsBundle.message("inspection.message.unexpected2"))
        }
        element.dotdotdot != null -> when {
            start == null && end == null -> deny(element.dotdotdot, holder, RsBundle.message("inspection.message.unexpected"))
            start == null -> deny(element.dotdotdot, holder, RsBundle.message("inspection.message.range.to.patterns.with.are.not.allowed"))
        }
    }
}

private fun checkTraitType(holder: AnnotationHolder, element: RsTraitType) {
    if (element.impl != null) return
    val bounds = element.polyboundList

    val lifetimeBounds = bounds.filter { it.bound.lifetime != null }
    if (lifetimeBounds.size > 1) {
        val fixes = lifetimeBounds.map { RemovePolyBoundFix(it) }.toList()
        RsDiagnostic.TooManyLifetimeBoundsOnTraitObjectError(element, fixes).addToHolder(holder)
    }

    if (bounds.none { it.bound.lifetime == null }) {
        RsDiagnostic.AtLeastOneTraitForObjectTypeError(element).addToHolder(holder)
    }
}

private fun checkUnderscoreExpr(holder: AnnotationHolder, element: RsUnderscoreExpr) {
    val isAllowed = run {
        val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return@run false
        if (binaryExpr.operatorType !is AssignmentOp) return@run false
        if (!binaryExpr.left.isAncestorOf(element)) return@run false
        true
    }

    if (!isAllowed) {
        deny(element, holder, RsBundle.message("inspection.message.in.expressions.can.only.be.used.on.left.hand.side.assignment"))
    }
}

private fun checkWherePred(holder: AnnotationHolder, boundPred: RsWherePred) {
    if (boundPred.forLifetimes?.lifetimeParameterList.orEmpty().isNotEmpty()) {
        for (bound in boundPred.typeParamBounds?.polyboundList.orEmpty()) {
            if (bound.forLifetimes?.lifetimeParameterList.orEmpty().isNotEmpty()) {
                val fixes = listOf(RemovePolyBoundFix(bound))
                RsDiagnostic.NestedQuantificationOfLifetimeBoundsError(bound, fixes).addToHolder(holder)
            }
        }
    }
}

private fun checkLambdaExpr(holder: AnnotationHolder, lambda: RsLambdaExpr) {
    val asyncElement = lambda.async
    if (asyncElement != null && lambda.move == null) {
        val valueParameterList = lambda.valueParameterList
        if (valueParameterList.valueParameterList.isNotEmpty()) {
            RsDiagnostic.AsyncNonMoveClosureWithParameters(asyncElement, valueParameterList).addToHolder(holder)
        }
    }
}

private fun checkIncDecOp(holder: AnnotationHolder, expr: RsExpr) {
    val operator = when (expr) {
        is RsPrefixIncExpr -> expr.inc
        is RsPostfixIncExpr -> expr.inc
        is RsPostfixDecExpr -> expr.dec
        else -> return
    }
    RsDiagnostic.RustHasNoIncDecOperator(operator).addToHolder(holder)
}

private fun checkReservedKeyword(holder: AnnotationHolder, item: PsiElement) {
    if (item.elementTypeOrNull == RsElementTypes.IDENTIFIER && item.text in RESERVED_KEYWORDS) {
        val macroRelatedParent = item.parentOfTypes(
            RsMacroArgument::class,
            RsMacroExpansionContents::class,
            RsMacroPatternContents::class,
            // Should be more precise, see `RsReservedKeywordAnnotatorTest.test annotate reserved keyword in attributes`
            RsMetaItemArgs::class,
            RsCompactTT::class
        )
        // it's not an error to use reserved keyword tokens as a part of macro call or macro definition
        if (macroRelatedParent != null) return

        val parent = item.parent
        val fixes = mutableListOf<LocalQuickFix>()

        if (parent is RsNameIdentifierOwner && parent.nameIdentifier == item) {
            fixes += EscapeKeywordFix(item, isKeyword = false)
        }

        RsDiagnostic.ReservedIdentifierIsUsed(item, fixes).addToHolder(holder)
    }
}

private fun checkCanUseVariadic(holder: AnnotationHolder, function: RsFunction) {
    if (function.isVariadic && function.owner == RsAbstractableOwner.Foreign && !function.isCOrCdeclAbi) {
        RsDiagnostic.VariadicParametersUsedOnNonCABIError(function).addToHolder(holder)
    }
}

private enum class TypeKind {
    LIFETIME,
    TYPE,
    CONST;

    val presentableName: String get() = name.lowercase()

    fun canStandAfter(prev: TypeKind): Boolean = this !== LIFETIME || prev === LIFETIME

    companion object {
        fun forType(seekingElement: PsiElement): TypeKind? =
            when (seekingElement) {
                is RsLifetimeParameter, is RsLifetime -> LIFETIME
                is RsTypeParameter, is RsTypeReference -> TYPE
                is RsConstParameter, is RsExpr -> CONST
                else -> null
            }
    }
}

private fun require(el: PsiElement?, holder: AnnotationHolder, @InspectionMessage message: String, vararg highlightElements: PsiElement?): Unit? =
    if (el != null || highlightElements.combinedRange == null) null
    else {
        holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(highlightElements.combinedRange!!).create()
    }

private fun deny(
    el: PsiElement?,
    holder: AnnotationHolder,
    @InspectionMessage message: String,
    vararg highlightElements: PsiElement?,
    severity: HighlightSeverity = HighlightSeverity.ERROR,
    fix: IntentionAction? = null
) {
    if (el == null) return
    holder.newAnnotation(severity, message)
        .range(highlightElements.combinedRange ?: el.textRange)
        .apply { if (fix != null) withFix(fix) }
        .create()
}

private val Array<out PsiElement?>.combinedRange: TextRange?
    get() = if (isEmpty())
        null
    else filterNotNull()
        .map { it.textRange }
        .reduce(TextRange::union)

private val PsiElement.rightVisibleLeaves: Sequence<PsiElement>
    get() = generateSequence(PsiTreeUtil.nextVisibleLeaf(this)) { el -> PsiTreeUtil.nextVisibleLeaf(el) }

private val String.firstLower: String
    get() = if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

