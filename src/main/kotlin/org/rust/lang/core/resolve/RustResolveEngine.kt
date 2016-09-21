package org.rust.lang.core.resolve

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDirectory
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
import org.rust.lang.core.psi.util.*
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.util.RustResolveUtil
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathHead
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny
import org.rust.lang.core.types.visitors.impl.RustTypeResolvingVisitor
import org.rust.utils.sequenceOfNotNull
import java.util.*


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

    fun resolve(type: RustUnresolvedType, pivot: RustCompositeElement): RustType =
        type.accept(RustTypeResolvingVisitor(pivot))

    /**
     * Resolves abstract qualified-path [ref] in such a way, like it was a qualified-reference
     * used at [pivot]
     */
    fun resolve(path: RustPath, pivot: RustCompositeElement): ResolveResult {
        val head = path.head
        val start: RustNamedElement? = when (head) {
            is RustPathHead.Absolute -> RustResolveUtil.getCrateRootModFor(pivot)
            is RustPathHead.Relative -> generateSequence(pivot.containingMod, { it.`super` }).elementAtOrNull(head.level)
            is RustPathHead.Named -> {
                RustResolveEngine.enumerateScopesFor(pivot)
                    .flatMap { declarations(it, Context(pivot = pivot)) }
                    .find { it.name == head.segment.name }
                    ?.element
            }
        }

        var current: RustNamedElement? = start
        for ((name) in path.segments) {
            if (current is RustResolveScope) {
                val next = current
                current = recursionGuard(pivot, Computable { resolveInside(next, name, pivot = pivot).element })
            }
        }

        return current.asResolveResult()
    }

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
        val receiverType = fieldExpr.expr.resolvedType.stripAllRefsIfAny()

        val id = (fieldExpr.fieldId.identifier ?: fieldExpr.fieldId.integerLiteral)!!
        val matching = when (id.elementType) {
            IDENTIFIER -> {
                val name = id.text
                when (receiverType) {
                    is RustStructType -> receiverType.item.fields.filter { it.name == name }
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
        val name = call.identifier.text

        return ResolveResult.buildFrom(
            receiverType.getNonStaticMethodsIn(call.project)
                .filter { it.name == name }
                .asIterable()
        )
    }

    //
    // TODO(kudinkin): Unify following?
    //

    fun resolveUseGlob(ref: RustUseGlobElement): ResolveResult = recursionGuard(ref, Computable {
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
            basePath.reference.resolve()
        else
        // `use ::{foo, bar}`
            RustResolveUtil.getCrateRootModFor(ref)

        when {
        // `use foo::{self}`
            ref.self != null && baseItem != null -> ResolveResult.Resolved(baseItem)

        // `use foo::{bar}`
            baseItem is RustResolveScope -> resolveInside(baseItem, ref.referenceName, pivot = ref)

            else -> ResolveResult.Unresolved
        }
    }) ?: ResolveResult.Unresolved

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
            return ResolveResult.Unresolved
        }

        val dir = parent.ownedDirectory

        val resolved = ref.possiblePaths.mapNotNull {
            dir?.findFileByRelativePath(it)?.rustMod
        }

        return when (resolved.size) {
            0    -> ResolveResult.Unresolved
            1    -> ResolveResult.Resolved    (resolved.single())
            else -> ResolveResult.Ambiguous   (resolved)
        }
    }

    fun resolveExternCrate(crate: RustExternCrateItemElement): ResolveResult {
        val name = crate.name ?: return ResolveResult.Unresolved
        val module = crate.module ?: return ResolveResult.Unresolved
        return module.project.getPsiFor(module.cargoProject?.findExternCrateRootByName(name))?.rustMod.asResolveResult()
    }

    /**
     * Lazily retrieves all elements visible in the particular [scope] at the [pivot], or just all
     * visible elements if [pivot] is null.
     */
    fun declarations(
        scope: RustResolveScope,
        pivot: RustCompositeElement? = null,
        searchFor: SearchFor = SearchFor.EVERYTHING
    ): Sequence<ScopeEntry> = declarations(scope, Context(pivot, searchFor = searchFor))

    fun enumerateScopesFor(pivot: RustCompositeElement): Sequence<RustResolveScope> =
        generateSequence(RustResolveUtil.getResolveScopeFor(pivot)) { parent ->
            if (parent is RustModItemElement)
                null
            else
                RustResolveUtil.getResolveScopeFor(parent)
        }
}


private fun resolveInside(scope: RustResolveScope, name: String, pivot: RustCompositeElement): RustResolveEngine.ResolveResult =
    declarations(scope, Context(pivot = pivot, searchFor = SearchFor.PRIVATE))
        .find { it.name == name }
        ?.element.asResolveResult()


private fun declarations(scope: RustResolveScope, context: Context): Sequence<ScopeEntry> =
    Sequence { RustScopeVisitor(context).compute(scope).iterator() }


private data class Context(
    val pivot: RustCompositeElement?,
    val visitedStarImports: Set<RustUseItemElement> = emptySet(),
    val searchFor: SearchFor = SearchFor.EVERYTHING
)

enum class SearchFor {
    /**
     * public, private and prelude names
     */
    EVERYTHING,

    /**
     * public and private
     */
    PRIVATE,

    //TODO: PUBLIC,
}


class ScopeEntry private constructor(
    val name: String,
    private val thunk: Lazy<RustNamedElement?>
) {
    val element: RustNamedElement? by thunk

    companion object {
        fun of(name: String, element: RustNamedElement): ScopeEntry = ScopeEntry(name, lazyOf(element))

        fun of(element: RustNamedElement): ScopeEntry? = element.name?.let { ScopeEntry.of(it, element) }

        fun lazy(name: String?, thunk: () -> RustNamedElement?): ScopeEntry? =
            name?.let {
                ScopeEntry(name, lazy(thunk))
            }
    }

    override fun toString(): String {
        return "ScopeEntryImpl(name='$name', thunk=$thunk)"
    }
}


private class RustScopeVisitor(
    val context: Context
) : RustComputingVisitor<Sequence<ScopeEntry>>() {

    override fun visitModItem(o: RustModItemElement) = visitMod(o)

    override fun visitFile(file: PsiFile) = visitMod(file as RustFile)

    override fun visitForExpr(o: RustForExprElement) = set {
        o.scopedForDecl.pat.scopeEntries
    }

    override fun visitScopedLetDecl(o: RustScopedLetDeclElement) = set {
        if (context.pivot == null || !PsiTreeUtil.isAncestor(o, context.pivot, true)) {
            o.pat.scopeEntries
        } else emptySequence()
    }

    override fun visitBlock(o: RustBlockElement) = set {
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
        val visibleLetDecls = if (context.pivot == null)
            allLetDecls
        else
            allLetDecls
                .dropWhile { PsiUtilCore.compareElementsByPosition(context.pivot, it) < 0 }
                // Drops at most one element
                .dropWhile { PsiTreeUtil.isAncestor(it, context.pivot, true) }

        val allBoundElements = visibleLetDecls.flatMap { it.pat.scopeEntries }

        // TODO: handle shadowing between blocks
        // `toList` to make it safe to iterate the sequence twice
        val declaredNames = HashSet<String>()
        val nonShadowed = allBoundElements.filter {
            val result = it.name !in declaredNames
            declaredNames += it.name
            result
        }.toList()

        nonShadowed.asSequence() + o.itemEntries(context)
    }

    override fun visitStructItem(o: RustStructItemElement) = set {
        sequenceOf(
            staticMethods(o),

            if (isContextLocalTo(o))
                o.typeParams.scopeEntries
            else
                emptySequence()
        ).flatten()
    }

    override fun visitEnumItem(o: RustEnumItemElement) = set {
        sequenceOf(
            staticMethods(o),

            if (isContextLocalTo(o))
                o.typeParams.scopeEntries
            else
                o.enumBody.enumVariantList.scopeEntries
        ).flatten()
    }

    override fun visitTraitItem(o: RustTraitItemElement) = set {
        if (isContextLocalTo(o)) {
            o.typeParams.scopeEntries + ScopeEntry.of(RustPath.CSELF, o)
        }

        else
            emptySequence()
    }

    override fun visitTypeItem(o: RustTypeItemElement) = set {
        if (isContextLocalTo(o))
            o.typeParams.scopeEntries
        else
            emptySequence()
    }

    override fun visitFnItem(o: RustFnItemElement) = visitFunction(o)

    override fun visitTraitMethodMember(o: RustTraitMethodMemberElement) = visitFunction(o)

    override fun visitImplMethodMember(o: RustImplMethodMemberElement) = visitFunction(o)

    override fun visitImplItem(o: RustImplItemElement) = set {
        if (isContextLocalTo(o)) {
            o.typeParams.scopeEntries + sequenceOfNotNull(ScopeEntry.lazy(RustPath.CSELF) {
                //TODO: handle types which are not `NamedElements` (e.g. tuples)
                (o.type as? RustPathTypeElement)?.path?.reference?.resolve()
            })
        } else
            emptySequence()
    }

    override fun visitLambdaExpr(o: RustLambdaExprElement) = set {
        o.parameters.parameterList.orEmpty()
            .asSequence()
            .flatMap { it.pat.scopeEntries }
    }

    override fun visitMatchArm(o: RustMatchArmElement) = set {
        // Rust allows to defined several patterns in the single match arm,
        // but they all must bind the same variables, hence we can inspect
        // only the first one.
        o.matchPat.patList.firstOrNull().scopeEntries
    }

    override fun visitWhileLetExpr(o: RustWhileLetExprElement) = visitScopedLetDecl(o.scopedLetDecl)

    override fun visitIfLetExpr(o: RustIfLetExprElement) = visitScopedLetDecl(o.scopedLetDecl)

    private fun visitMod(mod: RustMod) = set {
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
        val preludeSymbols = if (module == null || context.searchFor != SearchFor.EVERYTHING)
            emptySequence()
        else
            module.preludeModule?.rustMod?.let {
                declarations(it, context.copy(searchFor = SearchFor.PRIVATE))
            } ?: emptySequence()

        sequenceOf(
            mod.itemEntries(context),
            injectedCrates,
            preludeSymbols
        ).flatten()
    }

    private fun visitFunction(o: RustFnElement) = set {
        if (isContextLocalTo(o))
            sequenceOf(
                sequenceOfNotNull(o.parameters?.selfArgument?.let { ScopeEntry.of(it) }),
                o.parameters?.parameterList.orEmpty().asSequence().flatMap { it.pat.scopeEntries },
                o.typeParams.scopeEntries
            ).flatten()
        else
            emptySequence()
    }

    private fun isContextLocalTo(o: RustCompositeElement) = o.contains(context.pivot)

    private fun staticMethods(o: RustTypeBearingItemElement): Sequence<ScopeEntry> =
        RustImplIndex
            .findStaticMethodsFor(o.resolvedType, o.project)
            .scopeEntries

}


private fun RustItemsOwner.itemEntries(context: Context): Sequence<ScopeEntry> {
    val (wildCardImports, usualImports) = useDeclarations.partition { it.mul != null }

    return sequenceOf (
        // XXX: this must come before itemList to resolve `Box` from prelude. We need to handle cfg attributes to
        // fix this properly
        modDecls.asSequence().mapNotNull {
            ScopeEntry.lazy(it.name) { it.reference.resolve() }
        },

        allItemDefinitions.scopeEntries,

        foreignMods.asSequence().flatMap {
            it.foreignFnDeclList.scopeEntries + it.foreignStaticDeclList.scopeEntries
        },

        externCrates.asSequence().mapNotNull {
            ScopeEntry.lazy(it.alias?.name ?: it.name) { it.reference.resolve() }
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
    get() = asSequence().scopeEntries

private val RustPatElement?.scopeEntries: Sequence<ScopeEntry>
    get() = PsiTreeUtil.findChildrenOfType(this, RustPatBindingElement::class.java).scopeEntries

private val Sequence<RustNamedElement>.scopeEntries: Sequence<ScopeEntry>
    get() = mapNotNull { ScopeEntry.of(it) }


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
        when (part) {
            "."  -> {}
            ".." -> dir = dir.parentDirectory ?: return null
            else -> dir = dir.findSubdirectory(part) ?: return null
        }
    }

    return dir.findFile(fileName)
}


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
