/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector2
import org.rust.ide.utils.import.ImportContext2
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.stubs.RsStubLiteralKind
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.resolve
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.lang.utils.escapeRust
import org.rust.lang.utils.evaluation.evaluate
import org.rust.stdext.exhaustive
import org.rust.stdext.joinToWithBuffer

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTypeReference.getStubOnlyText(
    subst: Substitution = emptySubstitution,
    renderLifetimes: Boolean = true
): String = TypeSubstitutingPsiRenderer(PsiRenderingOptions(renderLifetimes), subst).renderTypeReference(this)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsValueParameterList.getStubOnlyText(
    subst: Substitution = emptySubstitution,
    renderLifetimes: Boolean = true
): String = TypeSubstitutingPsiRenderer(PsiRenderingOptions(renderLifetimes), subst).renderValueParameterList(this)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTraitRef.getStubOnlyText(subst: Substitution = emptySubstitution, renderLifetimes: Boolean = true): String =
    buildString { TypeSubstitutingPsiRenderer(PsiRenderingOptions(renderLifetimes), subst).appendPath(this, path) }

fun RsPsiRenderer.renderTypeReference(ref: RsTypeReference): String =
    buildString { appendTypeReference(this, ref) }

fun RsPsiRenderer.renderTraitRef(ref: RsTraitRef): String =
    buildString { appendPath(this, ref.path) }

fun RsPsiRenderer.renderValueParameterList(list: RsValueParameterList): String =
    buildString { appendValueParameterList(this, list) }

fun RsPsiRenderer.renderFunctionSignature(fn: RsFunction): String =
    buildString { appendFunctionSignature(this, fn) }

fun RsPsiRenderer.renderTypeAliasSignature(ta: RsTypeAlias, renderBounds: Boolean): String =
    buildString { appendTypeAliasSignature(this, ta, renderBounds) }

data class PsiRenderingOptions(
    val renderLifetimes: Boolean = true,
    /** Related to [RsPsiRenderer.appendFunctionSignature] */
    val renderGenericsAndWhere: Boolean = true,
    /** if `true`, renders `Bar` instead of `foo::Bar` */
    val shortPaths: Boolean = true,
)

@Suppress("MemberVisibilityCanBePrivate", "DuplicatedCode")
open class RsPsiRenderer(
    protected val options: PsiRenderingOptions
) {
    protected val renderLifetimes: Boolean get() = options.renderLifetimes
    protected val renderGenericsAndWhere: Boolean get() = options.renderGenericsAndWhere
    protected val shortPaths: Boolean get() = options.shortPaths

    open fun appendFunctionSignature(sb: StringBuilder, fn: RsFunction) {
        if (fn.isAsync) {
            sb.append("async ")
        }
        if (fn.isConst) {
            sb.append("const ")
        }
        if (fn.isUnsafe) {
            sb.append("unsafe ")
        }
        if (fn.isActuallyExtern) {
            sb.append("extern ")
            val abiName = fn.abiName
            if (abiName != null) {
                sb.append("\"")
                sb.append(abiName)
                sb.append("\" ")
            }
        }
        sb.append("fn ")
        sb.append(fn.escapedName ?: "")
        val typeParameterList = fn.typeParameterList
        if (typeParameterList != null && renderGenericsAndWhere) {
            appendTypeParameterList(sb, typeParameterList)
        }
        val valueParameterList = fn.valueParameterList
        if (valueParameterList != null) {
            appendValueParameterList(sb, valueParameterList)
        }
        val retType = fn.retType
        if (retType != null) {
            sb.append(" -> ")
            val retTypeReference = retType.typeReference
            if (retTypeReference != null) {
                appendTypeReference(sb, retTypeReference)
            }
        }
        val whereClause = fn.whereClause
        if (whereClause != null && renderGenericsAndWhere) {
            appendWhereClause(sb, whereClause)
        }
    }

    open fun appendTypeAliasSignature(sb: StringBuilder, ta: RsTypeAlias, renderBounds: Boolean) {
        sb.append("type ")
        sb.append(ta.escapedName ?: "")
        val typeParameterList = ta.typeParameterList
        if (typeParameterList != null && renderGenericsAndWhere) {
            appendTypeParameterList(sb, typeParameterList)
        }
        val whereClause = ta.whereClause
        if (whereClause != null && renderGenericsAndWhere) {
            appendWhereClause(sb, whereClause)
        }
        val typeParamBounds = ta.typeParamBounds
        if (typeParamBounds != null && renderBounds) {
            appendTypeParamBounds(sb, typeParamBounds)
        }
    }

    private fun appendWherePred(sb: StringBuilder, pred: RsWherePred) {
        val lifetime = pred.lifetime
        val type = pred.typeReference
        if (lifetime != null) {
            sb.append(lifetime.name)
            val bounds = pred.lifetimeParamBounds
            if (bounds != null) {
                appendLifetimeBounds(sb, bounds)
            }
        } else if (type != null) {
            val forLifetimes = pred.forLifetimes
            if (renderLifetimes && forLifetimes != null) {
                appendForLifetimes(sb, forLifetimes)
            }
            appendTypeReference(sb, type)
            val typeParamBounds = pred.typeParamBounds
            if (typeParamBounds != null) {
                appendTypeParamBounds(sb, typeParamBounds)
            }
        }
    }

    private fun appendWhereClause(sb: StringBuilder, whereClause: RsWhereClause) {
        sb.append(" where ")
        whereClause.wherePredList.joinToWithBuffer(sb, separator = ", ") {
            appendWherePred(sb, this)
        }
    }

    private fun appendTypeParamBounds(sb: StringBuilder, bounds: RsTypeParamBounds) {
        sb.append(": ")
        bounds.polyboundList.joinToWithBuffer(sb, " + ") {
            appendPolybound(sb, this)
        }
    }

    open fun appendTypeParameterList(sb: StringBuilder, list: RsTypeParameterList) {
        sb.append("<")
        list.stubChildrenOfType<RsElement>().joinToWithBuffer(sb, separator = ", ") {
            when (this) {
                is RsLifetimeParameter -> {
                    sb.append(name)
                    val bounds = lifetimeParamBounds
                    if (bounds != null) {
                        appendLifetimeBounds(sb, bounds)
                    }
                }
                is RsTypeParameter -> {
                    sb.append(name)
                    val bounds = typeParamBounds
                    if (bounds != null) {
                        sb.append(": ")
                        bounds.polyboundList.joinToWithBuffer(sb, " + ") {
                            appendPolybound(sb, this)
                        }
                    }
                    val defaultValue = typeReference
                    if (defaultValue != null) {
                        sb.append(" = ")
                        appendTypeReference(sb, defaultValue)
                    }
                }
                is RsConstParameter -> {
                    sb.append("const ")
                    sb.append(name ?: "_")
                    val type = typeReference
                    if (type != null) {
                        sb.append(": ")
                        appendTypeReference(sb, type)
                    }
                }
            }
        }
        sb.append(">")
    }

    private fun appendLifetimeBounds(sb: StringBuilder, bounds: RsLifetimeParamBounds) {
        sb.append(": ")
        bounds.lifetimeList.joinToWithBuffer(sb, separator = " + ") { it.append(name) }
    }

    open fun appendValueParameterList(
        sb: StringBuilder,
        list: RsValueParameterList
    ) {
        sb.append("(")
        val selfParameter = list.selfParameter
        val valueParameterList = list.valueParameterList
        if (selfParameter != null) {
            appendSelfParameter(sb, selfParameter)
            if (valueParameterList.isNotEmpty()) {
                sb.append(", ")
            }
        }
        valueParameterList.joinToWithBuffer(sb, separator = ", ") { sb1 ->
            sb1.append(patText ?: "_")
            sb1.append(": ")
            val typeReference = typeReference
            if (typeReference != null) {
                appendTypeReference(sb1, typeReference)
            } else {
                sb1.append("()")
            }
        }
        sb.append(")")
    }

    open fun appendSelfParameter(
        sb: StringBuilder,
        selfParameter: RsSelfParameter
    ) {
        val typeReference = selfParameter.typeReference
        if (typeReference != null) {
            sb.append("self: ")
            appendTypeReference(sb, typeReference)
        } else {
            if (selfParameter.isRef) {
                sb.append("&")
                val lifetime = selfParameter.lifetime
                if (renderLifetimes && lifetime != null) {
                    appendLifetime(sb, lifetime)
                    sb.append(" ")
                }
                sb.append(if (selfParameter.mutability.isMut) "mut " else "")
            }
            sb.append("self")
        }
    }

    open fun appendTypeReference(sb: StringBuilder, type: RsTypeReference) {
        when (type) {
            is RsParenType -> {
                sb.append("(")
                type.typeReference?.let { appendTypeReference(sb, it) }
                sb.append(")")
            }

            is RsTupleType -> {
                val types = type.typeReferenceList
                if (types.size == 1) {
                    sb.append("(")
                    appendTypeReference(sb, types.single())
                    sb.append(",)")
                } else {
                    types.joinToWithBuffer(sb, ", ", "(", ")") { appendTypeReference(it, this) }
                }
            }

            is RsBaseType -> when (val kind = type.kind) {
                RsBaseTypeKind.Unit -> sb.append("()")
                RsBaseTypeKind.Never -> sb.append("!")
                RsBaseTypeKind.Underscore -> sb.append("_")
                is RsBaseTypeKind.Path -> appendPath(sb, kind.path)
            }

            is RsRefLikeType -> {
                if (type.isPointer) {
                    sb.append(if (type.mutability.isMut) "*mut " else "*const ")
                } else if (type.isRef) {
                    sb.append("&")
                    val lifetime = type.lifetime
                    if (renderLifetimes && lifetime != null) {
                        appendLifetime(sb, lifetime)
                        sb.append(" ")
                    }
                    if (type.mutability.isMut) sb.append("mut ")
                }
                type.typeReference?.let { appendTypeReference(sb, it) }
            }

            is RsArrayType -> {
                sb.append("[")
                type.typeReference?.let { appendTypeReference(sb, it) }
                if (!type.isSlice) {
                    val arraySizeExpr = type.expr
                    sb.append("; ")
                    if (arraySizeExpr != null) {
                        appendConstExpr(sb, arraySizeExpr, TyInteger.USize.INSTANCE)
                    } else {
                        sb.append("{}")
                    }
                }
                sb.append("]")
            }

            is RsFnPointerType -> {
                if (type.isUnsafe) {
                    sb.append("unsafe ")
                }
                if (type.isExtern) {
                    sb.append("extern ")
                    val abiName = type.abiName
                    if (abiName != null) {
                        sb.append("\"")
                        sb.append(abiName)
                        sb.append("\" ")
                    }
                }
                sb.append("fn")
                appendValueParameterListTypes(sb, type.valueParameters)
                appendRetType(sb, type.retType)
            }

            is RsTraitType -> {
                sb.append(if (type.isImpl) "impl " else "dyn ")
                type.polyboundList.joinToWithBuffer(sb, " + ") {
                    appendPolybound(sb, this)
                }
            }

            is RsMacroType -> {
                appendPath(sb, type.macroCall.path)
                sb.append("!(")
                type.macroCall.macroBody?.let { sb.append(it) }
                sb.append(")")
            }
        }
    }

    private fun appendPolybound(sb: StringBuilder, polyBound: RsPolybound) {
        val forLifetimes = polyBound.forLifetimes
        if (renderLifetimes && forLifetimes != null) {
            appendForLifetimes(sb, forLifetimes)
        }
        if (polyBound.hasQ) {
            sb.append("?")
        }

        val bound = polyBound.bound
        val lifetime = bound.lifetime
        if (renderLifetimes && lifetime != null) {
            sb.append(lifetime.referenceName)
        } else {
            bound.traitRef?.path?.let { appendPath(sb, it) }
        }
    }

    private fun appendForLifetimes(sb: StringBuilder, forLifetimes: RsForLifetimes) {
        sb.append("for<")
        forLifetimes.lifetimeParameterList.joinTo(sb, ", ") {
            it.name ?: "'_"
        }
        sb.append("> ")
    }

    open fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        sb.append(lifetime.referenceName)
    }

    open fun appendPath(
        sb: StringBuilder,
        path: RsPath
    ) {
        appendPathWithoutArgs(sb, path)
        appendPathArgs(sb, path)
    }

    protected open fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath) {
        val qualifier = path.path
        if (!shortPaths && qualifier != null) {
            appendPath(sb, qualifier)
        }
        val typeQual = path.typeQual
        if (typeQual != null) {
            appendTypeQual(sb, typeQual)
        }
        if (path.hasColonColon) {
            sb.append("::")
        }
        sb.append(path.referenceName.orEmpty())
    }

    protected open fun appendTypeQual(sb: StringBuilder, typeQual: RsTypeQual) {
        sb.append("<")
        appendTypeReference(sb, typeQual.typeReference)
        val traitRef = typeQual.traitRef
        if (traitRef != null) {
            sb.append(" as ")
            appendPath(sb, traitRef.path)
        }
        sb.append(">")
        sb.append("::")
    }

    private fun appendPathArgs(sb: StringBuilder, path: RsPath) {
        val inAngles = path.typeArgumentList // Foo<...>
        val fnSugar = path.valueParameterList // &dyn FnOnce(...) -> i32
        if (inAngles != null) {
            val lifetimeArguments = inAngles.lifetimeList
            val typeArguments = inAngles.typeReferenceList
            val constArguments = inAngles.exprList
            val assocTypeBindings = inAngles.assocTypeBindingList

            val hasLifetimes = renderLifetimes && lifetimeArguments.isNotEmpty()
            val hasTypeReferences = typeArguments.isNotEmpty()
            val hasConstArguments = constArguments.isNotEmpty()
            val hasAssocTypeBindings = assocTypeBindings.isNotEmpty()

            if (hasLifetimes || hasTypeReferences || hasConstArguments || hasAssocTypeBindings) {
                sb.append("<")
                if (hasLifetimes) {
                    lifetimeArguments.joinToWithBuffer(sb, ", ") { appendLifetime(it, this) }
                    if (hasTypeReferences || hasConstArguments || hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                if (hasTypeReferences) {
                    typeArguments.joinToWithBuffer(sb, ", ") { appendTypeReference(it, this) }
                    if (hasConstArguments || hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                if (hasConstArguments) {
                    constArguments.joinToWithBuffer(sb, ", ") { appendConstExpr(it, this) }
                    if (hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                assocTypeBindings.joinToWithBuffer(sb, ", ") { sb ->
                    appendPath(sb, this.path)
                    sb.append("=")
                    typeReference?.let { appendTypeReference(sb, it) }
                }
                sb.append(">")
            }
        } else if (fnSugar != null) {
            appendValueParameterListTypes(sb, fnSugar.valueParameterList)
            appendRetType(sb, path.retType)
        }
    }

    protected open fun appendRetType(sb: StringBuilder, retType: RsRetType?) {
        val retTypeRef = retType?.typeReference
        if (retTypeRef != null) {
            sb.append(" -> ")
            appendTypeReference(sb, retTypeRef)
        }
    }

    protected open fun appendValueParameterListTypes(
        sb: StringBuilder,
        list: List<RsValueParameter>
    ) {
        list.joinToWithBuffer(sb, separator = ", ", prefix = "(", postfix = ")") { sb ->
            typeReference?.let { appendTypeReference(sb, it) }
        }
    }

    protected open fun appendConstExpr(
        sb: StringBuilder,
        expr: RsExpr,
        expectedTy: Ty = expr.type
    ) {
        when (expr) {
            is RsPathExpr -> appendPath(sb, expr.path)
            is RsLitExpr -> appendLitExpr(sb, expr)
            is RsBlockExpr -> appendBlockExpr(sb, expr)
            is RsUnaryExpr -> appendUnaryExpr(sb, expr)
            is RsBinaryExpr -> appendBinaryExpr(sb, expr)
            else -> sb.append("{}")
        }
    }

    protected open fun appendLitExpr(sb: StringBuilder, expr: RsLitExpr) {
        when (val kind = expr.stubKind) {
            is RsStubLiteralKind.Boolean -> sb.append(kind.value.toString())
            is RsStubLiteralKind.Integer -> sb.append(kind.value?.toString() ?: "")
            is RsStubLiteralKind.Float -> sb.append(kind.value?.toString() ?: "")
            is RsStubLiteralKind.Char -> {
                if (kind.isByte) {
                    sb.append("b")
                }
                sb.append("'")
                sb.append(kind.value.orEmpty().escapeRust())
                sb.append("'")
            }
            is RsStubLiteralKind.String -> {
                if (kind.isByte) {
                    sb.append("b")
                }
                sb.append('"')
                sb.append(kind.value.orEmpty().escapeRust())
                sb.append('"')
            }
            null -> "{}"
        }.exhaustive
    }

    protected open fun appendBlockExpr(sb: StringBuilder, expr: RsBlockExpr) {
        val isTry = expr.isTry
        val isUnsafe = expr.isUnsafe
        val isAsync = expr.isAsync
        val isConst = expr.isConst
        val tailExpr = expr.block.expandedTailExpr

        if (isTry) {
            sb.append("try ")
        }
        if (isUnsafe) {
            sb.append("unsafe ")
        }
        if (isAsync) {
            sb.append("async ")
        }
        if (isConst) {
            sb.append("const ")
        }

        if (tailExpr == null) {
            sb.append("{}")
        } else {
            sb.append("{ ")
            appendConstExpr(sb, tailExpr)
            sb.append(" }")
        }
    }

    protected open fun appendUnaryExpr(sb: StringBuilder, expr: RsUnaryExpr) {
        val sign = when (expr.operatorType) {
            UnaryOperator.REF -> "&"
            UnaryOperator.REF_MUT -> "&mut "
            UnaryOperator.DEREF -> "*"
            UnaryOperator.MINUS -> "-"
            UnaryOperator.NOT -> "!"
            UnaryOperator.BOX -> "box "
            UnaryOperator.RAW_REF_CONST -> "&raw const "
            UnaryOperator.RAW_REF_MUT -> "&raw mut "
        }
        sb.append(sign)
        val innerExpr = expr.expr
        if (innerExpr != null) {
            appendConstExpr(sb, innerExpr)
        }
    }

    protected open fun appendBinaryExpr(sb: StringBuilder, expr: RsBinaryExpr) {
        val sign = when (val op = expr.operatorType) {
            is ArithmeticOp -> op.sign
            is ArithmeticAssignmentOp -> op.sign
            AssignmentOp.EQ -> "="
            is ComparisonOp -> op.sign
            is EqualityOp -> op.sign
            LogicOp.AND -> "&&"
            LogicOp.OR -> "||"
        }
        appendConstExpr(sb, expr.left)
        sb.append(" ")
        sb.append(sign)
        sb.append(" ")
        val right = expr.right
        if (right != null) {
            appendConstExpr(sb, right)
        }
    }
}

open class TypeSubstitutingPsiRenderer(
    options: PsiRenderingOptions,
    private val subst: Substitution
) : RsPsiRenderer(options) {
    override fun appendTypeReference(sb: StringBuilder, ref: RsTypeReference) {
        val ty = ref.type
        if (ty is TyTypeParameter && subst[ty] != null) {
            sb.append(ty.substAndGetText(subst))
        } else {
            super.appendTypeReference(sb, ref)
        }
    }

    override fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        val resolvedLifetime = lifetime.resolve()
        val substitutedLifetime = if (resolvedLifetime is ReEarlyBound) subst[resolvedLifetime] else null
        if (substitutedLifetime is ReEarlyBound) {
            sb.append(substitutedLifetime.parameter.name)
        } else {
            sb.append(lifetime.referenceName)
        }
    }

    override fun appendConstExpr(
        sb: StringBuilder,
        expr: RsExpr,
        expectedTy: Ty
    ) {
        when (val const = expr.evaluate(expectedTy).substitute(subst)) { // may trigger resolve
            is CtValue -> sb.append(const)
            is CtConstParameter -> {
                val wrapParameterInBraces = expr.stubParent is RsTypeArgumentList

                if (wrapParameterInBraces) {
                    sb.append("{ ")
                }
                sb.append(const.toString())
                if (wrapParameterInBraces) {
                    sb.append(" }")
                }
            }
            else -> sb.append("{}")
        }
    }
}

open class PsiSubstitutingPsiRenderer(
    options: PsiRenderingOptions,
    private val substitutions: List<RsPsiSubstitution>
) : RsPsiRenderer(options) {
    override fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath) {
        val replaced = when (val resolved = path.reference?.resolve()) {
            is RsTypeParameter -> when (val s = typeSubst(resolved)) {
                is RsPsiSubstitution.Value.Present -> when (s.value) {
                    is RsPsiSubstitution.TypeValue.InAngles -> {
                        super.appendTypeReference(sb, s.value.value)
                        true
                    }
                    is RsPsiSubstitution.TypeValue.FnSugar -> false
                }
                is RsPsiSubstitution.Value.DefaultValue -> {
                    super.appendTypeReference(sb, s.value.value)
                    true
                }
                else -> false
            }
            is RsConstParameter -> when (val s = constSubst(resolved)) {
                is RsPsiSubstitution.Value.Present -> {
                    when (s.value) {
                        is RsExpr -> appendConstExpr(sb, s.value)
                        is RsTypeReference -> appendTypeReference(sb, s.value)
                    }
                    true
                }
                is RsPsiSubstitution.Value.DefaultValue -> {
                    appendConstExpr(sb, s.value)
                    true
                }
                else -> false
            }
            else -> false
        }
        if (!replaced) {
            super.appendPathWithoutArgs(sb, path)
        }
    }

    override fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        val resolvedLifetime = lifetime.reference.resolve()
        val substitutedLifetime = if (resolvedLifetime is RsLifetimeParameter) {
            regionSubst(resolvedLifetime)
        } else {
            null
        }
        when (substitutedLifetime) {
            is RsPsiSubstitution.Value.Present -> sb.append(substitutedLifetime.value.name)
            else -> sb.append(lifetime.referenceName)
        }
    }

    private fun regionSubst(lifetime: RsLifetimeParameter?): RsPsiSubstitution.Value<RsLifetime, Nothing>? {
        return substitutions.firstNotNullOfOrNull { it.regionSubst[lifetime] }
    }
    private fun constSubst(const: RsConstParameter?): RsPsiSubstitution.Value<RsElement, RsExpr>? {
        return substitutions.firstNotNullOfOrNull { it.constSubst[const] }
    }
    private fun typeSubst(type: RsTypeParameter?): RsPsiSubstitution.Value<RsPsiSubstitution.TypeValue, RsPsiSubstitution.TypeDefault>? {
        return substitutions.firstNotNullOfOrNull { it.typeSubst[type] }
    }
}

class ImportingPsiRenderer(
    options: PsiRenderingOptions,
    substitutions: List<RsPsiSubstitution>,
    private val context: RsElement
) : PsiSubstitutingPsiRenderer(options, substitutions) {

    private val importContext = ImportContext2.from(context, ImportContext2.Type.OTHER)

    private val visibleNames: Pair<MutableMap<Pair<String, Namespace>, RsElement>, MutableMap<RsElement, String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val nameToElement = mutableMapOf<Pair<String, Namespace>, RsElement>()
        val elementToName = mutableMapOf<RsElement, String>()
        processNestedScopesUpwards(context, TYPES_N_VALUES, createProcessor {
            val element = it.element as? RsNamedElement ?: return@createProcessor false
            for (namespace in element.namespaces) {
                nameToElement[it.name to namespace] = element
                if (it.name != "_" && element !in elementToName) {
                    elementToName[element] = it.name
                }
            }
            false
        })
        nameToElement to elementToName
    }
    private val visibleNameToElement: MutableMap<Pair<String, Namespace>, RsElement> get() = visibleNames.first
    private val visibleElementToName: MutableMap<RsElement, String> get() = visibleNames.second

    private val itemsToImportMut: MutableSet<ImportCandidate> = mutableSetOf()
    val itemsToImport: Set<ImportCandidate> get() = itemsToImportMut

    override fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath) {
        val pathReferenceName = path.referenceName
        val tryImportPath1 = path.parent !is RsPath &&
            TyPrimitive.fromPath(path) == null &&
            path.basePath().referenceName != "Self" &&
            path.basePath().typeQual == null
        if (tryImportPath1 && pathReferenceName != null) {
            val resolved = path.reference?.resolve()
            val tryImportPath2 = resolved !is RsTypeParameter
                && resolved !is RsConstParameter
                && resolved !is RsMacroDefinitionBase
                && resolved !is RsMod
            if (tryImportPath2 && resolved is RsQualifiedNamedElement) {
                val visibleElementName = visibleElementToName[resolved]
                if (visibleElementName != null) {
                    sb.append(visibleElementName)
                } else {
                    val importCandidate = importContext?.let {
                        ImportCandidatesCollector2.findImportCandidate(it, resolved)
                    }
                    if (importCandidate == null) {
                        val resolvedCrate = resolved.containingCrate
                        if (resolvedCrate == null || resolvedCrate == context.containingCrate) {
                            sb.append("crate")
                        } else {
                            sb.append(resolvedCrate.normName)
                        }
                        sb.append(resolved.crateRelativePath)
                    } else {
                        val ns = if (path.parent is RsExpr) {
                            Namespace.Values
                        } else {
                            Namespace.Types
                        }
                        val elementInScopeWithSameName = visibleNameToElement[pathReferenceName to ns]
                        val isNameConflict = elementInScopeWithSameName != null && elementInScopeWithSameName != resolved
                        if (isNameConflict) {
                            val qualifiedPath = importCandidate.info.usePath
                            sb.append(trySimplifyPath(path, qualifiedPath) ?: qualifiedPath)
                        } else {
                            itemsToImportMut += importCandidate
                            visibleElementToName[resolved] = pathReferenceName
                            for (namespace in resolved.namespaces) {
                                visibleNameToElement[pathReferenceName to namespace] = resolved
                            }
                            sb.append(pathReferenceName)
                        }
                    }
                }
                return
            }
        }

        super.appendPathWithoutArgs(sb, path)
    }

    private fun trySimplifyPath(originPath: RsPath, qualifiedPath: String): String? {
        val newPath = RsCodeFragmentFactory(originPath.project).createPath(
            qualifiedPath,
            context,
            RustParserUtil.PathParsingMode.TYPE,
            originPath.allowedNamespaces()
        ) ?: return null

        val segmentsReversed = generateSequence(newPath) { it.path }.toList()

        var simplifiedSegmentCount = 1
        var firstSegmentName: String? = null

        for (s in segmentsReversed.asSequence().drop(1)) {
            val resolved = s.reference?.resolve() ?: return null
            simplifiedSegmentCount++
            firstSegmentName = visibleElementToName[resolved]
            if (firstSegmentName != null) break
        }

        return if (firstSegmentName == null || simplifiedSegmentCount >= segmentsReversed.size) {
            null
        } else {
            "$firstSegmentName::" + segmentsReversed
                .take(simplifiedSegmentCount - 1)
                .asReversed()
                .joinToString(separator = "::") { it.referenceName.orEmpty() }
        }
    }
}
