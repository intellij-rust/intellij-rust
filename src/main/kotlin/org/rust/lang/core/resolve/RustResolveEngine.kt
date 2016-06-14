package org.rust.lang.core.resolve

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.getPsiFor
import org.rust.cargo.util.preludeModule
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustTokenElementTypes.IDENTIFIER
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.possiblePaths
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.util.RustResolveUtil
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.RustTypeResolvingVisitor


object RustResolveEngine {

    open class ResolveResult private constructor(val resolved: RustNamedElement?) : com.intellij.psi.ResolveResult {

        companion object {
            fun buildFrom(candidates: Iterable<RustNamedElement>): ResolveResult {
                return when (candidates.count()) {
                    1       -> ResolveResult.Resolved(candidates.first())
                    0       -> ResolveResult.Unresolved
                    else    -> ResolveResult.Ambiguous(candidates)
                }
            }
        }

        override fun getElement():      RustNamedElement? = resolved
        override fun isValidResult():   Boolean           = resolved != null

        /**
         * Designates resolve-engine failure to properly resolve item
         */
        object Unresolved : ResolveResult(null)

        /**
         * Designates resolve-engine failure to properly recognise target item
         * among the possible candidates
         */
        class Ambiguous(val candidates: Iterable<RustNamedElement>) : ResolveResult(null)

        /**
         * Designates resolve-engine successfully resolved given target
         */
        class Resolved(resolved: RustNamedElement) : ResolveResult(resolved)
    }

    fun resolve(type: RustUnresolvedType): RustType =
        type.accept(RustTypeResolvingVisitor())


    /**
     * Resolves `qualified-reference` bearing PSI-elements
     *
     * NOTE: This operate on PSI to extract all the necessary (yet implicit) resolving-context
     */
    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult =
        Resolver.resolve(ref)

    /**
     * Resolves references to struct's fields inside destructuring [RustStructExprElement]
     */
    fun resolveStructExprField(structExpr: RustStructExprElement, fieldName: String): ResolveResult {
        val matching = structExpr   .fields
                                    .filter { it.name == fieldName }

        return ResolveResult.buildFrom(matching)
    }

    /**
     * Resolves references to struct's fields inside [RustFieldExprElement]
     */
    fun resolveFieldExpr(fieldExpr: RustFieldExprElement): ResolveResult {
        val receiverType = fieldExpr.expr.resolvedType

        val id = (fieldExpr.fieldId.identifier ?: fieldExpr.fieldId.integerLiteral)!!
        val matching = when (id.elementType) {
            IDENTIFIER -> {
                val name = id.text
                when (receiverType) {
                    is RustStructType -> receiverType.struct.fields.filter { it.name == name }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }

        return ResolveResult.buildFrom(matching)
    }

    /**
     * Resolves method-call expressions
     */
    fun resolveMethodCallExpr(call: RustMethodCallExprElement): ResolveResult {
        val receiverType = call.expr.resolvedType

        return call.identifier?.let {
            val name = it.text
            val matching: Iterable<RustNamedElement> =
                when (receiverType) {
                    is RustStructType -> receiverType.nonStaticMethods.filter { it.name == name }

                    else -> emptyList()
                }

            ResolveResult.buildFrom(matching)
        } ?: ResolveResult.Unresolved
    }

    //
    // TODO(kudinkin): Unify following?
    //

    fun resolveUseGlob(ref: RustUseGlobElement): ResolveResult =
        Resolver.resolveUseGlob(ref)

    /**
     * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
     *
     *  ```
     *  // foo.rs
     *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir
     *
     *  pub mod nested {
     *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs`
     *  }
     *
     *  ```
     *
     *  | A module without a body is loaded from an external file, by default with the same name as the module,
     *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
     *  | from a subdirectory path that mirrors the module hierarchy.
     *
     * Reference:
     *      https://github.com/rust-lang/rust/blob/master/src/doc/reference.md#modules
     */
    fun resolveModDecl(ref: RustModDeclItemElement): ResolveResult {
        val parent  = ref.containingMod
        val name    = ref.name

        if (parent == null || name == null) {
            return RustResolveEngine.ResolveResult.Unresolved
        }

        val dir = parent.ownedDirectory

        val resolved = ref.possiblePaths.mapNotNull {
            dir?.findFileByRelativePath(it)?.rustMod
        }

        return when (resolved.size) {
            0    -> RustResolveEngine.ResolveResult.Unresolved
            1    -> RustResolveEngine.ResolveResult.Resolved    (resolved.single())
            else -> RustResolveEngine.ResolveResult.Ambiguous   (resolved)
        }
    }

    fun resolveExternCrate(crate: RustExternCrateItemElement): ResolveResult {
        val name = crate.name ?: return ResolveResult.Unresolved
        val module = crate.module ?: return ResolveResult.Unresolved
        return module.project.getPsiFor(module.cargoProject?.findExternCrateRootByName(name))?.rustMod.asResolveResult()
    }

    /**
     * Lazily retrieves all elements visible in the particular [scope] at the [place], or just all
     * visible elements if [place] is null.
     */
    fun declarations(scope: RustResolveScope, place: RustCompositeElement? = null): Sequence<RustNamedElement> =
        declarations(scope, Context(place)).mapNotNull { it.element }
}

private object Resolver {

    /**
     * Resolves `qualified-reference` bearing PSI-elements
     *
     * For more details check out [RustResolveEngine.resolve]
     */
    fun resolve(ref: RustQualifiedReferenceElement): RustResolveEngine.ResolveResult = recursionGuard(ref) {
        val modulePrefix = ref.relativeModulePrefix
        when (modulePrefix) {
            is RelativeModulePrefix.Invalid        -> RustResolveEngine.ResolveResult.Unresolved
            is RelativeModulePrefix.AncestorModule -> resolveAncestorModule(ref, modulePrefix).asResolveResult()
            is RelativeModulePrefix.NotRelative    -> {
                val qual = ref.qualifier
                if (qual == null) {
                    resolveIn(enumerateScopesFor(ref), ref)
                } else {
                    val parent = resolve(qual).element
                    when (parent) {
                        is RustMod      -> resolveIn(sequenceOf(parent), ref)
                        is RustEnumItemElement -> resolveIn(sequenceOf(parent), ref)
                        else            -> RustResolveEngine.ResolveResult.Unresolved
                    }
                }
            }
        }
    } ?: RustResolveEngine.ResolveResult.Unresolved

    /**
     * Resolves `use-glob`s, ie:
     *
     *  ```
     *  use foo::bar::{baz as boo}
     *  use foo::*
     *  ```
     */
    fun resolveUseGlob(ref: RustUseGlobElement): RustResolveEngine.ResolveResult = recursionGuard(ref) {
        val basePath = ref.basePath

        // This is not necessarily a module, e.g.
        //
        //   ```
        //   fn foo() {}
        //
        //   mod inner {
        //       use foo::{self};
        //   }
        //   ```
        val baseItem = if (basePath != null)
            resolve(basePath).element
        else
            // `use ::{foo, bar}`
            RustResolveUtil.getCrateRootModFor(ref)

        when {
            // `use foo::{self}`
            ref.self != null && baseItem != null -> RustResolveEngine.ResolveResult.Resolved(baseItem)

            // `use foo::{bar}`
            baseItem is RustResolveScope -> resolveIn(sequenceOf(baseItem), ref)

            else -> RustResolveEngine.ResolveResult.Unresolved
        }
    } ?: RustResolveEngine.ResolveResult.Unresolved

    private fun resolveAncestorModule(
        ref: RustQualifiedReferenceElement,
        modulePrefix: RelativeModulePrefix.AncestorModule
    ): RustMod? {
        var result: RustMod? = ref.containingMod
        for (i in 0 until modulePrefix.level) {
            result = result?.`super`
        }
        return result
    }

    private fun resolveIn(scopes: Sequence<RustResolveScope>, ref: RustReferenceElement): RustResolveEngine.ResolveResult {
        return scopes
            .flatMap { declarations(it, Context(place = ref)) }
            .find { it.name == ref.referenceName }
            ?.let { it.element }
            .asResolveResult()
    }

}


fun enumerateScopesFor(ref: RustQualifiedReferenceElement): Sequence<RustResolveScope> {
    if (ref.isRelativeToCrateRoot) {
        return listOfNotNull(RustResolveUtil.getCrateRootModFor(ref)).asSequence()
    }

    return generateSequence(RustResolveUtil.getResolveScopeFor(ref)) { parent ->
        when (parent) {
            is RustModItemElement  -> null
            else            -> RustResolveUtil.getResolveScopeFor(parent)
        }
    }
}


private fun RustNamedElement?.asResolveResult(): RustResolveEngine.ResolveResult =
    if (this == null)
        RustResolveEngine.ResolveResult.Unresolved
    else
        RustResolveEngine.ResolveResult.Resolved(this)


private fun PsiDirectory.findFileByRelativePath(path: String): PsiFile? {
    val parts = path.split("/")
    val fileName = parts.lastOrNull() ?: return null

    var dir = this
    for (part in parts.dropLast(1)) {
        dir = dir.findSubdirectory(part) ?: return null
    }

    return dir.findFile(fileName)
}


private fun declarations(scope: RustResolveScope, context: Context): Sequence<ScopeEntry> = Sequence {
    val visitor = RustScopeVisitor(context)
    scope.accept(visitor)
    visitor.result.iterator()
}


private data class Context(
    val place: RustCompositeElement?,
    val inPrelude: Boolean = false,
    val visitedStarImports: Set<RustUseItemElement> = emptySet()
)


private class ScopeEntry private constructor(
    val name: String,
    private val thunk: Lazy<RustNamedElement?>
) {
    val element: RustNamedElement? by thunk

    companion object {
        fun of(element: RustNamedElement): ScopeEntry? = element.name?.let {
            ScopeEntry(it, lazyOf(element))
        }

        fun lazy(name: String?, thunk: () -> RustNamedElement?): ScopeEntry? = name?.let {
            ScopeEntry(name, lazy(thunk))
        }
    }

    override fun toString(): String {
        return "ScopeEntryImpl(name='$name', thunk=$thunk)"
    }
}


private class RustScopeVisitor(
    val context: Context
) : RustElementVisitor() {
    lateinit var result: Sequence<ScopeEntry>

    override fun visitElement(element: PsiElement): Unit =
        throw IllegalStateException("Unhandled RustResolveScope: $element")

    override fun visitModItem(o: RustModItemElement) {
        visitMod(o)
    }

    override fun visitFile(file: PsiFile) {
        visitMod(file as RustFile)
    }

    override fun visitForExpr(o: RustForExprElement) {
        result = o.scopedForDecl.boundElements.scopeEntries
    }

    override fun visitScopedLetDecl(o: RustScopedLetDeclElement) {
        result = if (context.place == null || !PsiTreeUtil.isAncestor(o, context.place, true)) {
            o.boundElements.scopeEntries
        } else emptySequence()
    }

    override fun visitBlock(o: RustBlockElement) {
        // If place is specified in context, we want to filter out
        // all non strictly preceding let declarations.
        //
        // ```
        // let x = 92; // visible
        // let x = x;  // not visible
        //         ^ context.place
        // let x = 62; // not visible
        // ```
        val allLetDecls = o.stmtList.asReversed().asSequence().filterIsInstance<RustLetDeclElement>()
        val visibleLetDecls = if (context.place == null)
            allLetDecls
        else
            allLetDecls
                .dropWhile { PsiUtilCore.compareElementsByPosition(context.place, it) < 0 }
                // Drops at most one element
                .dropWhile { PsiTreeUtil.isAncestor(it, context.place, true) }

        result = visibleLetDecls.flatMap { it.boundElements.scopeEntries } + o.itemEntries(context)
    }

    override fun visitStructItem(o: RustStructItemElement) {
        result = o.typeParamEntries
    }

    override fun visitEnumItem(o: RustEnumItemElement) {
        result = o.enumBody.enumVariantList.scopeEntries + o.typeParamEntries
    }

    override fun visitTraitItem(o: RustTraitItemElement) {
        result = o.typeParamEntries
    }

    override fun visitTypeItem(o: RustTypeItemElement) {
        result = o.typeParamEntries
    }

    override fun visitFnItem(o: RustFnItemElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitTraitMethodMember(o: RustTraitMethodMemberElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitImplMethodMember(o: RustImplMethodMemberElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitImplItem(o: RustImplItemElement) {
        result = o.typeParamEntries
    }

    override fun visitLambdaExpr(o: RustLambdaExprElement) {
        result = o.parameters.parameterList.orEmpty().asSequence()
            .flatMap { it.boundElements.scopeEntries }
    }

    override fun visitMatchArm(o: RustMatchArmElement) {
        result = o.matchPat.boundElements.scopeEntries
    }

    override fun visitWhileLetExpr(o: RustWhileLetExprElement) {
        visitScopedLetDecl(o.scopedLetDecl)
    }

    override fun visitIfLetExpr(o: RustIfLetExprElement) {
        visitScopedLetDecl(o.scopedLetDecl)
    }

    fun visitMod(mod: RustMod) {
        val module = mod.module
        // Rust injects implicit `extern crate std` in every crate root module unless it is
        // a `#![no_std]` crate, in which case `extern crate core` is injected.
        // The stdlib lib itself is `#![no_std]`.
        // We inject both crates for simplicity for now.
        val injectedCrates = if (module == null || !mod.isCrateRoot)
            emptySequence()
        else
            sequenceOf(AutoInjectedCrates.std, AutoInjectedCrates.core).mapNotNull { crateName ->
                ScopeEntry.lazy(crateName) {
                    module.project.getPsiFor(module.cargoProject?.findExternCrateRootByName(crateName))?.rustMod
                }
            }

        // Rust injects implicit `use std::prelude::v1::*` into every module.
        val preludeSymbols = if (module == null || context.inPrelude)
            emptySequence()
        else
            module.preludeModule?.rustMod?.let { declarations(it, context.copy(inPrelude = true)) } ?: emptySequence()

        result = sequenceOf(
            mod.itemEntries(context),
            injectedCrates,
            preludeSymbols
        ).flatten()
    }

    fun visitFunctionLike(params: RustParametersElement?, fn: RustGenericDeclaration) {
        result = listOfNotNull(params?.selfArgument?.let { ScopeEntry.of(it) }).asSequence() +
            params?.parameterList.orEmpty().asSequence().flatMap { it.boundElements.scopeEntries } +
            fn.typeParamEntries
    }
}


private fun RustItemsOwner.itemEntries(context: Context): Sequence<ScopeEntry> {
    val (wildCardImports, usualImports) = useDeclarations.partition { it.mul != null }

    return sequenceOf (
        // XXX: this must come before itemList to resolve `Box` from prelude. We need to handle cfg attributes to
        // fix this properly
        modDecls.asSequence().mapNotNull {
            ScopeEntry.lazy(it.name) { it.reference?.resolve() }
        },

        allItemDefinitions.scopeEntries,

        externCrates.asSequence().mapNotNull {
            ScopeEntry.lazy(it.alias?.name ?: it.name) { it.reference?.resolve() }
        },

        usualImports.asSequence().flatMap { it.nonWildcardEntries() },

        // wildcard imports have low priority
        wildCardImports.asSequence().flatMap { it.wildcardEntries(context) }
    ).flatten()
}


private fun RustUseItemElement.wildcardEntries(context: Context): Sequence<ScopeEntry> {
    if (this in context.visitedStarImports) return emptySequence()
    // Recursively step into `use foo::*`
    val mod = path?.reference?.resolve() as? RustResolveScope ?: return emptySequence()
    return declarations(mod, context.copy(visitedStarImports = context.visitedStarImports + this))
}


private fun RustUseItemElement.nonWildcardEntries(): Sequence<ScopeEntry> {
    val globList = useGlobList
    if (globList == null) {
        val path = path ?: return emptySequence()
        // use foo::bar [as baz];
        val entry = ScopeEntry.lazy(alias?.name ?: path.referenceName) { path.reference.resolve() }
        return listOfNotNull(entry).asSequence()
    }

    return globList.useGlobList.asSequence().mapNotNull { glob ->
        val name = listOfNotNull(
            glob.alias?.name, // {foo as bar};
            glob.self?.let { path?.referenceName }, // {self}
            glob.referenceName // {foo}
        ).firstOrNull()

        ScopeEntry.lazy(name) { glob.reference.resolve() }
    }
}


private val Collection<RustNamedElement>.scopeEntries: Sequence<ScopeEntry>
    get() = asSequence().mapNotNull { ScopeEntry.of(it) }


private val RustGenericDeclaration.typeParamEntries: Sequence<ScopeEntry>
    get() = genericParams?.typeParamList.orEmpty().scopeEntries


/**
 * Helper to debug complex iterator pipelines
 */
@Suppress("unused")
private fun<T> Sequence<T>.inspect(f: (T) -> Unit = { println("inspecting $it") }): Sequence<T> {
    return map { it ->
        f(it)
        it
    }
}
