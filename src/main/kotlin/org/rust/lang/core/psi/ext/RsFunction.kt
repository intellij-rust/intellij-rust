/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.CompilerFeature
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsFunctionStub
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import javax.swing.Icon

val RsFunction.block: RsBlock? get() = PsiTreeUtil.getChildOfType(this, RsBlock::class.java)

val RsFunction.isAssocFn: Boolean get() = !hasSelfParameters && owner.isImplOrTrait
val RsFunction.isMethod: Boolean get() = hasSelfParameters && owner.isImplOrTrait

val RsFunction.isTest: Boolean
    get() = queryAttributes.isTest

val RsFunction.isMain: Boolean get() {
    val parent = context
    if (parent !is RsFile || !parent.isCrateRoot) return false
    if (parent.queryAttributes.hasAttribute("no_main")) return false
    val targetKind = containingCargoTarget?.kind ?: return false
    if (!(targetKind.isBin || targetKind.isExampleBin || targetKind.isCustomBuild)) return false
    val hasStartFeature = CompilerFeature.START.availability(parent) == FeatureAvailability.AVAILABLE
    return (hasStartFeature && queryAttributes.hasAtomAttribute("start")) || name == "main"
}

private val QueryAttributes<*>.isTest
    get() = metaItems.mapNotNull { it.path?.referenceName }.any { it.contains("test") } || hasAtomAttribute("quickcheck")

val RsFunction.isBench: Boolean
    get() = queryAttributes.hasAtomAttribute("bench")

val RsFunction.isConst: Boolean
    get() {
        val stub = greenStub
        return stub?.isConst ?: (const != null)
    }

/** Returns `true` if the function has `extern` modifier or located inside a certain `extern {}` block */
val RsFunction.isActuallyExtern: Boolean
    get() = isExtern || stubParent is RsForeignModItem

/** Returns `true` if the function has `extern` modifier */
val RsFunction.isExtern: Boolean
    get() {
        val stub = greenStub
        return stub?.isExtern ?: (externAbi != null)
    }

val RsFunction.isVariadic: Boolean
    get() {
        val stub = greenStub
        return stub?.isVariadic ?: (valueParameterList?.variadic != null)
    }

val RsFunction.literalAbiName: String?
    get() {
        val stub = greenStub
        if (stub != null) {
            return stub.abiName
        }
        return abi?.litExpr?.stringValue
    }

val RsFunction.actualAbiName: String
    get() = literalAbiName ?: abi?.let { "C" } ?: "Rust"

val RsFunction.isCOrCdeclAbi
    get() = actualAbiName == "C" || actualAbiName == "cdecl"

/**
 * Those function parameters that are not disabled by cfg attributes.
 *
 * Should be used in code analysis: name resolution, type inference, inspections, annotations, etc.
 */
val RsFunction.valueParameters: List<RsValueParameter>
    get() = rawValueParameters.filter { it.existsAfterExpansionSelf }

/**
 * All function parameters.
 *
 * Should be used in code (PSI) manipulations: intentions, quick-fixes, refactorings, code generation, etc.
 */
val RsFunction.rawValueParameters: List<RsValueParameter>
    get() = valueParameterList?.valueParameterList.orEmpty()

val RsFunction.selfParameter: RsSelfParameter?
    get() = valueParameterList?.selfParameter

val RsFunction.default: PsiElement?
    get() = node.findChildByType(RsElementTypes.DEFAULT)?.psi

val RsFunction.isAsync: Boolean
    get() = greenStub?.isAsync ?: (node.findChildByType(RsElementTypes.ASYNC) != null)

val RsFunction.hasSelfParameters: Boolean
    get() = greenStub?.hasSelfParameters ?: (selfParameter != null)

val RsFunction.title: String
    get() = when (owner) {
        is RsAbstractableOwner.Free -> "Function `$name`"
        is RsAbstractableOwner.Foreign -> "Foreign function `$name`"
        is RsAbstractableOwner.Trait, is RsAbstractableOwner.Impl ->
            if (isAssocFn) "Associated function `$name`" else "Method `$name`"
    }

val RsFunction.rawReturnType: Ty
    get() {
        val retType = retType ?: return TyUnit.INSTANCE
        return retType.typeReference?.rawType ?: TyUnknown
    }

val RsFunction.normReturnType: Ty
    get() {
        val retType = retType ?: return TyUnit.INSTANCE
        return retType.typeReference?.normType ?: TyUnknown
    }

val RsFunction.abi: RsExternAbi? get() = externAbi ?: (parent as? RsForeignModItem)?.externAbi

val RsFunction.declaration: String
    get() = buildString {
        val const = const
        if (const != null)
            append("${const.text} ")
        append(identifier.text)
        val currentTypeParameterList = typeParameterList
        if (currentTypeParameterList != null)
            append(currentTypeParameterList.text)
        val valueParameterList = valueParameterList
        if (valueParameterList != null)
            append(valueParameterList.text)
        val retType = retType
        if (retType != null)
            append(retType.text)
    }

/**
 * A function is unsafe if defined with `unsafe` modifier or if defined inside a certain `extern`
 * block. But [RsFunction.isUnsafe] takes into account only `unsafe` modifier. [isActuallyUnsafe]
 * takes into account both cases.
 */
val RsFunction.isActuallyUnsafe: Boolean
    get() {
        if (isUnsafe) return true
        val context = context
        return if (context is RsForeignModItem) {
            // functions inside `extern` block are unsafe in most cases
            when {
                // #[wasm_bindgen] is a procedural macro that removes the following
                // extern block, so all function inside it become safe.
                // See https://github.com/rustwasm/wasm-bindgen
                context.queryAttributes.hasAttribute("wasm_bindgen") -> false
                // Some Rust intrinsics are safe. This info is hardcoded in compiler
                isIntrinsic -> name !in SAFE_INTRINSICS
                else -> true
            }
        } else {
            false
        }
    }

val RsFunction.isIntrinsic: Boolean
    get() {
        val context = context
        return context is RsForeignModItem && context.effectiveAbi == "rust-intrinsic"
    }


// Taken from https://github.com/rust-lang/rust/blob/6b4563bf93f4b103ed22507ed825008b89e4f5d9/compiler/rustc_typeck/src/check/intrinsic.rs#L65-L108
private val SAFE_INTRINSICS: Set<String> = hashSetOf(
    "abort",
    "size_of",
    "min_align_of",
    "needs_drop",
    "caller_location",
    "add_with_overflow",
    "sub_with_overflow",
    "mul_with_overflow",
    "wrapping_add",
    "wrapping_sub",
    "wrapping_mul",
    "saturating_add",
    "saturating_sub",
    "rotate_left",
    "rotate_right",
    "ctpop",
    "ctlz",
    "cttz",
    "bswap",
    "bitreverse",
    "discriminant_value",
    "type_id",
    "likely",
    "unlikely",
    "ptr_guaranteed_eq",
    "ptr_guaranteed_ne",
    "minnumf32",
    "minnumf64",
    "maxnumf32",
    "rustc_peek",
    "maxnumf64",
    "type_name",
    "forget",
    "black_box",
    "variant_count",
)

val RsFunction.isBangProcMacroDef: Boolean
    get() = queryAttributes.hasAtomAttribute("proc_macro")

val RsFunction.isAttributeProcMacroDef: Boolean
    get() = queryAttributes.hasAtomAttribute("proc_macro_attribute")

val RsFunction.isCustomDeriveProcMacroDef: Boolean
    get() = queryAttributes.isCustomDeriveProcMacroDef

val QueryAttributes<*>.isCustomDeriveProcMacroDef: Boolean
    get() = hasAttribute("proc_macro_derive")

val RsFunction.isProcMacroDef: Boolean
    get() = IS_PROC_MACRO_DEF_PROP.getByPsi(this)

val IS_PROC_MACRO_DEF_PROP: StubbedAttributeProperty<RsFunction, RsFunctionStub> =
    StubbedAttributeProperty(QueryAttributes<*>::isProcMacroDef, RsFunctionStub::mayBeProcMacroDef)

val RsFunction.procMacroName: String?
    get() = when {
        isBangProcMacroDef || isAttributeProcMacroDef -> name

        isCustomDeriveProcMacroDef -> {
            queryAttributes.getFirstArgOfSingularAttribute("proc_macro_derive")
        }

        else -> null
    }

val QueryAttributes<*>.isProcMacroDef
    get() = hasAnyOfAttributes(
        "proc_macro",
        "proc_macro_attribute",
        "proc_macro_derive"
    )

val RsFunction.preferredBraces: MacroBraces
    get() = greenStub?.preferredBraces ?: guessPreferredBraces()

private fun PsiReference.getFunctionCallUsage(): RsCallExpr? {
    val path = element
    val pathExpr = path.parent
    return pathExpr.parent as? RsCallExpr
}

private fun PsiReference.getMethodCallUsage(): RsMethodCall? = element as? RsMethodCall

private fun PsiReference.getReferenceUsage(): RsPath? {
    val path = element as? RsPath ?: return null
    return if (path.parent.parent is RsCallExpr) null
    else path
}

/**
 * Find all function calls that call this function.
 */
fun RsFunction.findFunctionCalls(scope: SearchScope? = null): Sequence<RsCallExpr> = searchReferences(scope)
    .asSequence()
    .mapNotNull { it.getFunctionCallUsage() }

/**
 * Find all method calls that call this function.
 */
fun RsFunction.findMethodCalls(scope: SearchScope? = null): Sequence<RsMethodCall> = searchReferences(scope)
    .asSequence()
    .mapNotNull { it.getMethodCallUsage() }

/**
 * Find all reference usages of this function, for example when the function is passed as a parameter to another
 * function.
 */
fun RsFunction.findReferenceUsages(scope: SearchScope? = null): Sequence<RsPath> = searchReferences(scope)
    .asSequence()
    .mapNotNull { it.getReferenceUsage() }


/**
 * Find all usages of this function. Depending on the type of the returned element, the usage is either:
 * RsCallExpr - function call (see [findFunctionCalls]).
 * RsMethodCall - method call (see [findMethodCalls]).
 * RsPath - reference usage (see [findReferenceUsages]).
 */
fun RsFunction.findUsages(scope: SearchScope? = null): Sequence<RsElement> = searchReferences(scope)
    .asSequence()
    .mapNotNull { it.getMethodCallUsage() ?: it.getFunctionCallUsage() ?: it.getReferenceUsage() }

abstract class RsFunctionImplMixin : RsStubbedNamedElementImpl<RsFunctionStub>, RsFunction, RsModificationTrackerOwner {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isAbstract: Boolean get() = greenStub?.isAbstract ?: (block == null)

    override val isUnsafe: Boolean get() = this.greenStub?.isUnsafe ?: (unsafe != null)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override fun getIcon(flags: Int): Icon = getIcon(flags, allowNameResolution = true)

    override fun getIcon(flags: Int, allowNameResolution: Boolean): Icon {
        val baseIcon = when (val owner = if (allowNameResolution) owner else ownerBySyntaxOnly) {
            is RsAbstractableOwner.Free, is RsAbstractableOwner.Foreign ->
                when {
                    allowNameResolution && isTest -> RsIcons.FUNCTION.addTestMark()
                    allowNameResolution && isProcMacroDef -> RsIcons.PROC_MACRO
                    else -> RsIcons.FUNCTION
                }

            is RsAbstractableOwner.Trait, is RsAbstractableOwner.Impl -> {
                val icon = when {
                    isAssocFn && isAbstract -> RsIcons.ABSTRACT_ASSOC_FUNCTION
                    isAssocFn -> RsIcons.ASSOC_FUNCTION
                    isAbstract -> RsIcons.ABSTRACT_METHOD
                    else -> RsIcons.METHOD
                }
                if (!owner.isInherentImpl) return icon
                icon
            }
        }
        return iconWithVisibility(flags, baseIcon)
    }

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override val modificationTracker: SimpleModificationTracker =
        SimpleModificationTracker()

    override fun incModificationCount(element: PsiElement): Boolean {
        val shouldInc = block?.isAncestorOf(element) == true && PsiTreeUtil.findChildOfAnyType(
            element,
            false,
            RsItemElement::class.java,
            RsMacro::class.java
        ) == null
        if (shouldInc) modificationTracker.incModificationCount()
        return shouldInc
    }

    override fun getNameIdentifier(): PsiElement? =
        getDeriveProcMacroNameIdentifier() ?: super.getNameIdentifier()

    /**
     * #[proc_macro_derive(Foo)]
     *                     ~~~ returns this identifier
     * pub fn func(_item: TokenStream) -> TokenStream { "".parse().unwrap() }
     *
     * returns null in case it is not possible to determine without resolve whether it is proc macro or not
     */
    private fun getDeriveProcMacroNameIdentifier(): PsiElement? {
        val stub = greenStub
        if (stub != null && !stub.mayBeProcMacroDef) return null  // fast path

        val metaItem = rawOuterMetaItems.singleOrNull {
            if (it.canBeAttrProcMacro()) return null
            it.name == "proc_macro_derive"
        } ?: return null
        return metaItem.metaItemArgs?.metaItemList?.firstOrNull()?.path?.identifier
    }

    val functionName: String?
        get() = if (isProcMacroDef) super.getNameIdentifier()?.unescapedText else name

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}

private fun RsMetaItem.canBeAttrProcMacro(): Boolean =
    name == "cfg_attr"
        || RsProcMacroPsiUtil.canBeProcMacroAttributeCallWithoutContextCheck(this, CustomAttributes.EMPTY)

/** See also [procMacroName] */
val RsFunction.functionName: String?
    get() = (this as RsFunctionImplMixin).functionName

private val RS_FN_KEYWORDS = tokenSetOf(
    RsElementTypes.VIS, RsElementTypes.DEFAULT, RsElementTypes.ASYNC, RsElementTypes.CONST,
    RsElementTypes.UNSAFE, RsElementTypes.EXTERN_ABI
)

val RsFunction.firstKeyword: PsiElement
    get() = node.findChildByType(RS_FN_KEYWORDS)?.psi ?: fn

val RsFunction.async: PsiElement?
    get() = node.findChildByType(RsElementTypes.ASYNC)?.psi
