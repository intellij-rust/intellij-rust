package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.getPsiFor
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment
import org.rust.lang.core.types.type
import org.rust.lang.core.types.stripAllRefsIfAny
import org.rust.lang.core.types.types.RustStructType
import org.rust.utils.sequenceOfNotNull
import java.util.*


object ResolveEngine {
    /**
     * Resolves abstract qualified-path [path] in such a way, like it was a qualified-reference
     * used at [pivot]
     */
    fun resolve(path: RustPath, pivot: RsCompositeElement, namespace: Namespace? = null, single: Boolean = true): List<RsCompositeElement> {
        val start: Sequence<ScopeEntry> = when (path) {
            is RustPath.CrateRelative -> sequenceOfNotNull(
                pivot.crateRoot?.let { ScopeEntry.of(it) }
            )

            is RustPath.ModRelative -> sequenceOfNotNull(
                generateSequence(pivot.containingMod, { it.`super` })
                    .elementAtOrNull(path.level)
                    ?.let { ScopeEntry.of(it) }
            )

            is RustPath.Named -> lexicalDeclarations(pivot)
                .filter { it.name == path.head.name }
        }

        val inUseDeclaration = pivot.parentOfType<RsUseItem>() != null
        var current: Sequence<ScopeEntry> = start
        for ((name) in path.segments) {
            val scope = current
                .filterByNamespace(Namespace.Types)
                .mapNotNull { it.element }
                .mapNotNull {
                    val containing = containingDeclarations(it)
                    val associated = if (inUseDeclaration) null else associatedDeclarations(it)
                    when {
                        containing != null && associated != null -> containing + associated
                        containing != null -> containing
                        else -> associated
                    }
                }
                .firstOrNull() ?: return emptyList()

            current = scope.filter { it.name == name }
        }

        val filteredByNs = if (namespace == null) current else current.filterByNamespace(namespace).take(if (single) 1 else Int.MAX_VALUE)
        return filteredByNs
            .mapNotNull { it.element }
            .toList()
    }

    data class Result(
        val element: RsNamedElement,
        val pkg: CargoWorkspace.Package
    )

    /**
     * Resolves an absolute path.
     */
    fun resolve(path: String, module: Module): Result? {
        val segments = path.segments
        if (segments.isEmpty()) return null
        val cargoProject = module.cargoWorkspace ?: return null
        val pkg = cargoProject.findPackage(segments[0].name) ?: return null
        val vfm = VirtualFileManager.getInstance()
        val rustPath = RustPath.CrateRelative(segments.drop(1))
        val el = pkg.targets.asSequence()
            .mapNotNull { vfm.findFileByUrl(it.crateRootUrl) }
            .mapNotNull { module.project.getPsiFor(it) as? RsCompositeElement }
            .flatMap { ResolveEngine.resolve(rustPath, it).asSequence() }
            .filterIsInstance(RsNamedElement::class.java)
            .firstOrNull() ?: return null
        return Result(el, pkg)
    }

    /**
     * Resolves references to struct's fields inside destructuring [RsStructExpr]
     */
    fun resolveStructExprField(structExpr: RsStructExpr, fieldName: String): List<RsNamedElement> =
        structExpr.fields.filter { it.name == fieldName }

    /**
     * Resolves references to struct's fields inside [RsFieldExpr]
     */
    fun resolveFieldExpr(fieldExpr: RsFieldExpr): List<RsCompositeElement> {
        val receiverType = fieldExpr.expr.type.stripAllRefsIfAny()
        val struct = (receiverType as? RustStructType)?.item ?: return emptyList()

        val name = fieldExpr.fieldName
        if (name != null) {
            return struct.namedFields.filter { it.name == name }
        }

        val index = fieldExpr.fieldIndex
        if (index != null) {
            return listOfNotNull(struct.positionalFields.getOrNull(index))
        }

        return emptyList()
    }

    fun resolveCallExpr(path: RustPath, element: RsPath, namespace: Namespace?): List<RsCompositeElement> {
        val fn = resolve(path, element, namespace, false).asSequence()
            .filterIsInstance<RsNamedElement>()
            .chooseMajor()
        return if (fn == null) emptyList() else listOf(fn)
    }

    /**
     * Resolves method-call expressions
     */
    fun resolveMethodCallExpr(call: RsMethodCallExpr): RsNamedElement? {
        val receiverType = call.expr.type
        val name = call.identifier.text
        return receiverType.getMethodsIn(call.project)
            .filter { it.name == name }
            .chooseMajor()
    }

    fun resolveUseGlob(ref: RsUseGlob): List<RsCompositeElement> = recursionGuard(ref, Computable {
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
            baseItem != null -> (containingDeclarations(baseItem) ?: emptySequence())
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
    fun resolveModDecl(modDecl: RsModDeclItem): RsNamedElement? {
        val dir = modDecl.containingMod.ownedDirectory ?: return null

        val psiManager = PsiManager.getInstance(modDecl.project)
        return modDecl.possiblePaths.mapNotNull { path ->
            dir.virtualFile.findFileByRelativePath(path)?.let { psiManager.findFile(it) }?.rustMod
        }.singleOrNull()
    }

    fun resolveExternCrate(crate: RsExternCrateItem): RsNamedElement? {
        val name = crate.name ?: return null
        val module = crate.module ?: return null
        return module.project.getPsiFor(module.cargoWorkspace?.findCrateByName(name)?.crateRoot)?.rustMod
    }

    private val String.segments: List<RustPathSegment>
        get() = splitToSequence("::")
            .map { RustPathSegment(it, emptyList()) }
            .toList()

    /**
     * Chooses the major element from the given sequence of candidates. For instance,
     * a function/method from inherent implementation takes precedence over trait implementations.
     */
    private fun Sequence<RsNamedElement>.chooseMajor(): RsNamedElement? =
        partition { it.isInherent }.let {
            when {
                it.first.isNotEmpty() -> it.first[0]
                it.second.isNotEmpty() -> it.second[0]
                else -> null
            }
        }
}

/**
 * Walk the tree up starting at [place] and collect all visible declarations
 * (local variables, items, imports)
 */
fun lexicalDeclarations(place: RsCompositeElement, stop: (PsiElement) -> Boolean = { false }): Sequence<ScopeEntry> =
    place.ancestors
        .takeWhileInclusive { it !is RsMod && !stop(it) }
        .flatMap { lexicalDeclarations(it, place, Context()) }

/**
 * Collect non-associated items declared inside [scope].
 * That is, collect structs, enums, enum variants, but not methods.
 */
fun containingDeclarations(scope: RsCompositeElement): Sequence<ScopeEntry>? =
    containingDeclarations(scope, Context())

private fun containingDeclarations(scope: RsCompositeElement, context: Context): Sequence<ScopeEntry>? =
    when (scope) {
        is RsFile -> itemDeclarations(scope, false, context) + injectedCrates(scope)
        is RsMod -> itemDeclarations(scope, false, context)
        is RsEnumItem -> scope.enumBody.enumVariantList.asScopeEntries()
        else -> null
    }

/**
 * Collect associated declarations, like methods and types (types are not handled yet).
 */
fun associatedDeclarations(scope: RsCompositeElement): Sequence<ScopeEntry>? {
    val type = when (scope) {
        is RsStructItem -> scope.type
        is RsEnumItem -> scope.type
        else -> return null
    }

    return RsImplIndex
        .findMethodsAndAssociatedFunctionsFor(type, scope.project)
        .mapNotNull { ScopeEntry.of(it) }
}


private data class Context(
    val visitedStarImports: Set<RsUseItem> = emptySet()
)


private fun lexicalDeclarations(
    scope: PsiElement,
    place: RsCompositeElement,
    context: Context
): Sequence<ScopeEntry> {
    return when (scope) {
        is RsFile ->
            sequenceOf(
                itemDeclarations(scope, true, context),
                injectedCrates(scope),
                preludeSymbols(scope.module, context)
            ).flatten()

        is RsModItem ->
            sequenceOf(
                itemDeclarations(scope, true, context),
                preludeSymbols(scope.module, context)
            ).flatten()

        is RsStructItem,
        is RsEnumItem,
        is RsTypeAlias -> {
            scope as RsGenericDeclaration
            scope.typeParameters.asScopeEntries()
        }

        is RsTraitItem ->
            scope.typeParameters.asScopeEntries() + ScopeEntry.of(RustPath.CSELF, scope)

        is RsImplItem -> {
            scope.typeParameters.asScopeEntries() +
                sequenceOfNotNull(ScopeEntry.lazy(RustPath.CSELF) {
                    //TODO: handle types which are not `NamedElements` (e.g. tuples)
                    (scope.typeReference as? RsBaseType)?.path?.reference?.resolve()
                })
        }

        is RsFunction -> {
            // ugly hack to avoid loading AST when resolving types
            // Ideally, we should thread namespace information to this point.
            if (place is RsTypeReference) {
                scope.typeParameters.asScopeEntries()
            } else {
                sequenceOf(
                    sequenceOfNotNull(scope.selfParameter?.let { ScopeEntry.of(it) }),
                    scope.valueParameters.asSequence().mapNotNull { it.pat }.flatMap { it.boundNames },
                    scope.typeParameters.asScopeEntries()
                ).flatten()
            }
        }

        is RsBlock -> {
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
                .filterIsInstance<RsLetDecl>()
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

        is RsForExpr -> {
            if (scope.expr?.isStrictAncestorOf(place) == true) return emptySequence()
            scope.pat?.boundNames ?: emptySequence()
        }

        is RsIfExpr -> scope.condition?.boundNames(place) ?: emptySequence()
        is RsWhileExpr -> scope.condition?.boundNames(place) ?: emptySequence()

        is RsLambdaExpr -> scope
            .valueParameterList.valueParameterList.asSequence()
            .mapNotNull { it.pat }
            .flatMap { it.boundNames }

        is RsMatchArm -> scope
            // Rust allows to defined several patterns in the single match arm,
            // but they all must bind the same variables, hence we can inspect
            // only the first one.
            .matchPat.patList.take(1).asSequence()
            .flatMap { it.boundNames }

        else -> emptySequence()
    }
}


private fun preludeSymbols(module: Module?, context: Context): Sequence<ScopeEntry> {
    return module?.preludeModule?.rustMod?.let {
        containingDeclarations(it, context)
    } ?: emptySequence()
}


private fun itemDeclarations(
    scope: RsItemsOwner,
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

private fun definedItems(scope: RsItemsOwner): Sequence<Pair<String, RsNamedElement>> {
    val inlineItems: Sequence<RsNamedElement> = sequenceOf(
        scope.functionList.asSequence(),
        scope.enumItemList.asSequence(),
        scope.modItemList.asSequence(),
        scope.constantList.asSequence(),
        scope.structItemList.asSequence(),
        scope.traitItemList.asSequence(),
        scope.typeAliasList.asSequence(),
        scope.foreignModItemList.asSequence().flatMap {
            it.functionList.asSequence<RsNamedElement>() + it.constantList.asSequence()
        }
    ).flatten()

    return sequenceOf(
        // XXX: this must come before itemList to resolve `Box` from prelude. We need to handle cfg attributes to
        // fix this properly
        scope.modDeclItemList.asSequence().mapNotNull { modDecl ->
            val name = modDecl.name
            val mod = modDecl.reference.resolve() as? RsMod
            if (name != null && mod != null) name to mod else null
        },

        inlineItems.mapNotNull { item ->
            item.name?.let { it to item }
        },

        scope.externCrateItemList.asSequence().mapNotNull { crate ->
            val name = crate.alias?.name ?: crate.name
            val mod = crate.reference.resolve() as? RsMod
            if (name != null && mod != null) name to mod else null
        }
    ).flatten()
}

private val RsPat.boundNames: Sequence<ScopeEntry>
    get() = PsiTreeUtil.findChildrenOfType(this, RsPatBinding::class.java)
        .asScopeEntries()

private fun RsCondition.boundNames(place: RsCompositeElement): Sequence<ScopeEntry> =
    if (this.isStrictAncestorOf(place)) emptySequence()
    else pat?.boundNames ?: emptySequence()

private fun injectedCrates(file: RsFile): Sequence<ScopeEntry> {
    val module = file.module
        ?: return emptySequence()
    val cargoProject = module.cargoWorkspace
        ?: return emptySequence()

    if (!file.isCrateRoot)
        return emptySequence()

    // Rust injects implicit `extern crate std` in every crate root module unless it is
    // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
    // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
    //
    // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
    // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
    val injected = when (file.attributes) {
        RsFile.Attributes.NONE -> AutoInjectedCrates.std
        RsFile.Attributes.NO_STD -> AutoInjectedCrates.core
        RsFile.Attributes.NO_CORE -> return emptySequence()
    }
    return sequenceOfNotNull(ScopeEntry.lazy(injected) {
        val crate = cargoProject.findCrateByName(injected)?.crateRoot
        module.project.getPsiFor(crate)?.rustMod
    })
}

private fun RsUseItem.wildcardEntries(context: Context): Sequence<ScopeEntry> {
    if (this in context.visitedStarImports) return emptySequence()
    // Recursively step into `use foo::*`
    val mod = path?.reference?.resolve() ?: return emptySequence()
    val newCtx = context.copy(visitedStarImports = context.visitedStarImports + this)
    return containingDeclarations(mod, newCtx) ?: emptySequence()
}

private fun RsUseItem.nonWildcardEntries(): Sequence<ScopeEntry> {
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

private val Module.preludeModule: PsiFile? get() {
    val stdlib = cargoWorkspace?.findCrateByName(AutoInjectedCrates.std)?.crateRoot
        ?: return null
    val preludeFile = stdlib.findFileByRelativePath("../prelude/v1.rs")
        ?: return null
    return project.getPsiFor(preludeFile)
}

private fun Collection<RsNamedElement>.asScopeEntries(): Sequence<ScopeEntry> =
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

private val RsCompositeElement.isInherent: Boolean
    get() = (parent as? RsImplItem)?.let { return@let if (it.traitRef == null) it else null } != null

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

