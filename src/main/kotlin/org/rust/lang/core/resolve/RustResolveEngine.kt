package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.cargoProject
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.getPsiFor
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.isSelf
import org.rust.lang.core.psi.impl.mixin.isStarImport
import org.rust.lang.core.psi.impl.mixin.possiblePaths
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.typeParams
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny
import org.rust.utils.sequenceOfNotNull
import java.util.*


object RustResolveEngine {
    data class Result(
        val element: RustNamedElement,
        val pkg: CargoProjectDescription.Package
    )

    /**
     * Resolves abstract qualified-path [path] in such a way, like it was a qualified-reference
     * used at [pivot]
     */
    fun resolve(path: RustPath, pivot: RustCompositeElement, namespace: Namespace? = null): List<RustCompositeElement> {
        val allNs = resolveAllNamespaces(path, pivot)
        val filteredByNs = if (namespace == null) allNs else allNs.filterByNamespace(namespace).take(1)
        return filteredByNs
            .mapNotNull { it.element }
            .toList()
    }

    /**
     * Resolves an absolute path.
     */
    fun resolve(path: String, module: Module): Result? {
        val segments = path.segments
        if (segments.isEmpty()) return null
        val cargoProject = module.cargoProject ?: return null
        val pkg = cargoProject.findPackage(segments[0].name) ?: return null
        val vfm = VirtualFileManager.getInstance()
        val rustPath = RustPath.CrateRelative(segments.drop(1))
        val el = pkg.targets.asSequence()
            .mapNotNull { vfm.findFileByUrl(it.crateRootUrl) }
            .mapNotNull { module.project.getPsiFor(it) as? RustCompositeElement }
            .flatMap { RustResolveEngine.resolve(rustPath, it).asSequence() }
            .filterIsInstance(RustNamedElement::class.java)
            .firstOrNull() ?: return null
        return Result(el, pkg)
    }

    /**
     * Resolves references to struct's fields inside destructuring [RustStructExprElement]
     */
    fun resolveStructExprField(structExpr: RustStructExprElement, fieldName: String): List<RustNamedElement> =
        structExpr.fields.filter { it.name == fieldName }

    /**
     * Resolves references to struct's fields inside [RustFieldExprElement]
     */
    fun resolveFieldExpr(fieldExpr: RustFieldExprElement): List<RustCompositeElement> {
        val receiverType = fieldExpr.expr.resolvedType.stripAllRefsIfAny()
        val struct = (receiverType as? RustStructType)?.item ?: return emptyList()

        val name = fieldExpr.fieldId.identifier
        val index = fieldExpr.fieldId.integerLiteral
        return when {
            name != null -> struct.namedFields.filter { it.name == name.text }
            index != null -> listOfNotNull(struct.positionalFields.getOrNull(index.text.toInt()))
            else -> error("Field expression without a field $fieldExpr")
        }
    }

    /**
     * Resolves method-call expressions
     */
    fun resolveMethodCallExpr(call: RustMethodCallExprElement): RustNamedElement? {
        val receiverType = call.expr.resolvedType
        val name = call.identifier.text

        return receiverType.getNonStaticMethodsIn(call.project)
            .find { it.name == name }
    }

    fun resolveUseGlob(ref: RustUseGlobElement): List<RustCompositeElement> = recursionGuard(ref, Computable {
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
        val baseItem = (if (basePath != null)
            basePath.reference.resolve()
        else
        // `use ::{foo, bar}`
            ref.crateRoot)

        when {
        // `use foo::{self}`
            ref.isSelf && baseItem != null -> listOf(baseItem)

        // `use foo::{bar}`
            baseItem != null -> (outerDeclarations(baseItem) ?: emptySequence())
                .filter { it.name == ref.referenceName }
                .mapNotNull { it.element }
                .toList()
            else -> emptyList()
        }
    }) ?: emptyList()

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
    fun resolveModDecl(modDecl: RustModDeclItemElement): RustNamedElement? {
        val dir = modDecl.containingMod.ownedDirectory ?: return null

        val psiManager = PsiManager.getInstance(modDecl.project)
        return modDecl.possiblePaths.mapNotNull { path ->
            dir.virtualFile.findFileByRelativePath(path)?.let { psiManager.findFile(it) }?.rustMod
        }.singleOrNull()
    }

    fun resolveExternCrate(crate: RustExternCrateItemElement): RustNamedElement? {
        val name = crate.name ?: return null
        val module = crate.module ?: return null
        return module.project.getPsiFor(module.cargoProject?.findExternCrateRootByName(name))?.rustMod
    }

    private val String.segments: List<RustPathSegment>
        get() = splitToSequence("::")
            .map { RustPathSegment(it, emptyList()) }
            .toList()
}


/**
 * Collect the name visible at [scope] from outside. In other words, find all things,
 * that `scope::thing` is a valid path. Return `null` if scope can't have outer declarations
 * (for example, this will return `null` for functions and some sequence for a moudle)
 */
fun outerDeclarations(scope: RustCompositeElement): Sequence<ScopeEntry>? =
    outerDeclarations(scope, Context())

/**
 * Walk the tree up starting at [place] and collect all visible declarations
 * (local variables, items, imports)
 */
fun innerDeclarations(place: RustCompositeElement, stop: (PsiElement) -> Boolean = { false }): Sequence<ScopeEntry> =
    innerDeclarations(place, Context(), stop)


private fun resolveAllNamespaces(path: RustPath, pivot: RustCompositeElement): Sequence<ScopeEntry> {
    val start: Sequence<ScopeEntry> = when (path) {
        is RustPath.CrateRelative -> sequenceOfNotNull(
            pivot.crateRoot?.let { ScopeEntry.of(it) }
        )

        is RustPath.ModRelative -> sequenceOfNotNull(
            generateSequence(pivot.containingMod, { it.`super` })
                .elementAtOrNull(path.level)
                ?.let { ScopeEntry.of(it) }
        )

        is RustPath.Named -> innerDeclarations(pivot)
            .filter { it.name == path.head.name }
    }

    var current: Sequence<ScopeEntry> = start
    for ((name) in path.segments) {
        val scope = current
            .filterByNamespace(Namespace.Types)
            .mapNotNull { it.element }
            .mapNotNull(::outerDeclarations)
            .firstOrNull() ?: return emptySequence()

        current = scope.filter { it.name == name }
    }

    return current
}

private data class Context(
    val visitedStarImports: Set<RustUseItemElement> = emptySet()
)

private fun outerDeclarations(scope: RustCompositeElement, context: Context = Context()): Sequence<ScopeEntry>? = when (scope) {
    is RustFile -> itemDeclarations(scope, false, context) + injectedCrates(scope)
    is RustMod -> itemDeclarations(scope, false, context)
    is RustStructItemElement -> methods(scope)
    is RustEnumItemElement ->
        scope.enumBody.enumVariantList.asScopeEntries() + methods(scope)
    else -> null
}

private fun innerDeclarations(
    place: RustCompositeElement,
    context: Context = Context(),
    stop: (PsiElement) -> Boolean
): Sequence<ScopeEntry> =
    place.ancestors
        .takeWhileInclusive { it !is RustMod && !stop(it) }
        .flatMap { innerDeclarationsIn(it, place, context) }

private fun preludeSymbols(module: Module?, context: Context): Sequence<ScopeEntry> {
    return module?.preludeModule?.rustMod?.let {
        outerDeclarations(it, context)
    } ?: emptySequence()
}

private fun innerDeclarationsIn(
    scope: PsiElement,
    place: RustCompositeElement,
    context: Context
): Sequence<ScopeEntry> {
    return when (scope) {
        is RustFile -> {
            sequenceOf(
                itemDeclarations(scope, true, context),
                injectedCrates(scope),
                preludeSymbols(scope.module, context)
            ).flatten()
        }

        is RustModItemElement -> {
            sequenceOf(
                itemDeclarations(scope, true, context),
                preludeSymbols(scope.module, context)
            ).flatten()
        }

        is RustStructItemElement,
        is RustEnumItemElement,
        is RustUnionItemElement,
        is RustTypeAliasElement -> {
            scope as RustGenericDeclaration
            scope.typeParams.asScopeEntries()
        }

        is RustTraitItemElement ->
            scope.typeParams.asScopeEntries() + ScopeEntry.of(RustPath.CSELF, scope)

        is RustImplItemElement -> {
            scope.typeParams.asScopeEntries() +
                sequenceOfNotNull(ScopeEntry.lazy(RustPath.CSELF) {
                    //TODO: handle types which are not `NamedElements` (e.g. tuples)
                    (scope.type as? RustPathTypeElement)?.path?.reference?.resolve()
                })
        }

        is RustFunctionElement -> {
            val selfArgument = scope.parameters?.selfArgument
            val arguments = scope.parameters?.parameterList.orEmpty().asSequence()

            sequenceOf(
                sequenceOfNotNull(selfArgument?.let { ScopeEntry.of(it) }),
                arguments.mapNotNull { it.pat }.flatMap { it.boundNames },
                scope.typeParams.asScopeEntries()
            ).flatten()
        }

        is RustBlockElement -> {
            // We want to filter out
            // all non strictly preceding let declarations.
            //
            // ```
            // let x = 92; // visible
            // let x = x;  // not visible
            //         ^ context.place
            // let x = 62; // not visible
            // ```
            val visiblePatterns = scope
                .stmtList.asReversed().asSequence()
                .filterIsInstance<RustLetDeclElement>()
                .dropWhile { PsiUtilCore.compareElementsByPosition(place, it) < 0 }
                // Drops at most one element
                .dropWhile { it.isStrictAncestorOf(place) }
                .mapNotNull { it.pat }

            val allBoundElements = visiblePatterns.flatMap { it.boundNames }

            // TODO: handle shadowing between blocks
            val declaredNames = HashSet<String>()
            val nonShadowed = allBoundElements.filter {
                val result = it.name !in declaredNames
                declaredNames += it.name
                result
            }.toList() // `toList` to make it safe to iterate the sequence twice

            nonShadowed.asSequence() + itemDeclarations(scope, true, context)
        }

        is RustForExprElement -> {
            if (scope.expr?.isStrictAncestorOf(place) ==  true) return emptySequence()
            scope.pat?.boundNames ?: emptySequence()
        }

        is RustIfExprElement -> scope.condition?.boundNames(place) ?: emptySequence()
        is RustWhileExprElement -> scope.condition?.boundNames(place) ?: emptySequence()

        is RustLambdaExprElement -> scope
            .parameters.parameterList.asSequence()
            .mapNotNull { it.pat }
            .flatMap { it.boundNames }

        is RustMatchArmElement -> scope
            // Rust allows to defined several patterns in the single match arm,
            // but they all must bind the same variables, hence we can inspect
            // only the first one.
            .matchPat.patList.take(1).asSequence()
            .flatMap { it.boundNames }

        else -> emptySequence()
    }
}

private fun itemDeclarations(
    scope: RustItemsOwner,
    isLocal: Boolean,
    context: Context
): Sequence<ScopeEntry> {
    val (starImports, itemImports) = scope.useItemList
        .filter { it.isPublic || isLocal }
        .partition { it.isStarImport }

    return sequenceOf(
        definedItems(scope).map { ScopeEntry.of(it.first, it.second) },

        itemImports.asSequence().flatMap { it.nonWildcardEntries() },

        // wildcard imports have low priority
        starImports.asSequence().flatMap { it.wildcardEntries(context) }
    ).flatten()
}

private fun definedItems(scope: RustItemsOwner): Sequence<Pair<String, RustNamedElement>> {
    val inlineItems: Sequence<RustNamedElement> = sequenceOf(
        scope.functionList.asSequence(),
        scope.enumItemList.asSequence(),
        scope.unionItemList.asSequence(),
        scope.modItemList.asSequence(),
        scope.constantList.asSequence(),
        scope.structItemList.asSequence(),
        scope.traitItemList.asSequence(),
        scope.typeAliasList.asSequence(),
        scope.foreignModItemList.asSequence().flatMap {
            it.functionList.asSequence<RustNamedElement>() + it.constantList.asSequence()
        }
    ).flatten()

    return sequenceOf(
        // XXX: this must come before itemList to resolve `Box` from prelude. We need to handle cfg attributes to
        // fix this properly
        scope.modDeclItemList.asSequence().mapNotNull { modDecl ->
            val name = modDecl.name
            val mod = modDecl.reference.resolve() as? RustMod
            if (name != null && mod != null) name to mod else null
        },

        inlineItems.mapNotNull { item ->
            item.name?.let { it to item }
        },

        scope.externCrateItemList.asSequence().mapNotNull { crate ->
            val name = crate.alias?.name ?: crate.name
            val mod = crate.reference.resolve() as? RustMod
            if (name != null && mod != null) name to mod else null
        }
    ).flatten()
}

private val RustPatElement.boundNames: Sequence<ScopeEntry>
    get() = PsiTreeUtil.findChildrenOfType(this, RustPatBindingElement::class.java)
        .asScopeEntries()

private fun RustConditionElement.boundNames(place: RustCompositeElement): Sequence<ScopeEntry> =
    if (this.isStrictAncestorOf(place)) emptySequence()
    else pat?.boundNames ?: emptySequence()

private fun injectedCrates(file: RustFile): Sequence<ScopeEntry> {
    val module = file.module
        ?: return emptySequence()
    val cargoProject = module.cargoProject
        ?: return emptySequence()

    if (!file.isCrateRoot)
        return emptySequence()

    // Rust injects implicit `extern crate std` in every crate root module unless it is
    // a `#![no_std]` crate, in which case `extern crate core` is injected.
    // The stdlib lib itself is `#![no_std]`.
    // We inject both crates for simplicity for now.
    return sequenceOf(AutoInjectedCrates.std, AutoInjectedCrates.core).mapNotNull { crateName ->
        ScopeEntry.lazy(crateName) {
            val crate = cargoProject.findExternCrateRootByName(crateName)
            module.project.getPsiFor(crate)?.rustMod
        }
    }
}

private fun RustUseItemElement.wildcardEntries(context: Context): Sequence<ScopeEntry> {
    if (this in context.visitedStarImports) return emptySequence()
    // Recursively step into `use foo::*`
    val mod = path?.reference?.resolve() ?: return emptySequence()
    val newCtx = context.copy(visitedStarImports = context.visitedStarImports + this)
    return outerDeclarations(mod, newCtx) ?: emptySequence()
}

private fun RustUseItemElement.nonWildcardEntries(): Sequence<ScopeEntry> {
    val globList = useGlobList
    if (globList == null) {
        val path = path ?: return emptySequence()
        // use foo::bar [as baz];
        val entry = ScopeEntry.multiLazy(alias?.name ?: path.referenceName) {
            path.reference.multiResolve()
        }
        return listOfNotNull(entry).asSequence()
    }

    return globList.useGlobList.asSequence().mapNotNull { glob ->
        val name = listOfNotNull(
            glob.alias?.name, // {foo as bar};
            (if (glob.isSelf) path?.referenceName else null), // {self}
            glob.referenceName // {foo}
        ).firstOrNull()

        ScopeEntry.multiLazy(name) { glob.reference.multiResolve() }
    }
}

private fun methods(o: RustTypeBearingItemElement): Sequence<ScopeEntry> =
    RustImplIndex
        .findMethodsFor(o.resolvedType, o.project)
        .mapNotNull { ScopeEntry.of(it) }

private val Module.preludeModule: PsiFile? get() {
    val stdlib = cargoProject?.findExternCrateRootByName(AutoInjectedCrates.std)
        ?: return null
    val preludeFile = stdlib.findFileByRelativePath("../prelude/v1.rs")
        ?: return null
    return project.getPsiFor(preludeFile)
}

private fun Collection<RustNamedElement>.asScopeEntries(): Sequence<ScopeEntry> =
    asSequence().mapNotNull { ScopeEntry.Companion.of(it) }


private fun <T> Sequence<T>.takeWhileInclusive(pred: (T) -> Boolean): Sequence<T> {
    var shouldContinue = true
    return takeWhile {
        val result = shouldContinue
        shouldContinue = pred(it)
        result
    }
}

private fun PsiElement.isStrictAncestorOf(child: PsiElement) =
    PsiTreeUtil.isAncestor(this, child, true)

/**
 * Helper to debug complex iterator pipelines
 */
@Suppress("unused")
private fun <T> Sequence<T>.inspect(f: (T) -> Unit = { println("inspecting $it") }): Sequence<T> {
    return map { it ->
        f(it)
        it
    }
}

