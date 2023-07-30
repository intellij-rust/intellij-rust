/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("LoopToCallChain")

package org.rust.lang.core.resolve

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ThreeState
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.RsConstants
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile.Attributes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.NameResolutionTestmarks.CrateRootModule
import org.rust.lang.core.resolve.NameResolutionTestmarks.ModRsFile
import org.rust.lang.core.resolve.NameResolutionTestmarks.SelfInGroup
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.indexes.RsMacroIndex
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.resolve2.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.openapiext.*
import org.rust.stdext.buildList
import org.rust.stdext.withPrevious

// IntelliJ Rust name resolution algorithm.
// Collapse all methods (`ctrl shift -`) to get a bird's eye view.
//
// The entry point is
// `process~X~ResolveVariants(x: RsReferenceElement, processor: RsResolveProcessor)`
// family of methods.
//
// Conceptually, each of these methods returns a sequence of `RsNameElement`s
// visible at the reference location. During completion, all of them are presented
// as completion variants, and during resolve only the one with the name matching
// the reference is selected.
//
// Instead of Kotlin `Sequence`'s, a callback (`RsResolveProcessor`) is used, because
// it gives **much** nicer stacktraces (we used to have `Sequence` here some time ago).
//
// Instead of using `RsNameElement` directly, `RsResolveProcessor` operates on `ScopeEntry`s.
// `ScopeEntry` allows to change the effective name of an element (for aliases) and to retrieve
// the actual element lazily.
//
// The `process~PsiElement~Declarations` family of methods list name elements belonging
// to a particular element (for example, variants of an enum).
//
// Technicalities:
//
//   * We can get into infinite loop during name resolution. This is handled by
//     `RsReferenceCached`.
//   * The results of name resolution are cached and invalidated on every code change.
//     Caching also is handled by `RsReferenceCached`.
//   * Ideally, all of the methods except for `processLexicalDeclarations` should operate on stubs only.
//   * Rust uses two namespaces for declarations ("types" and "values"). The necessary namespace is
//     determined by the syntactic position of the reference in `processResolveVariants` function and
//     is passed down to the `processDeclarations` functions.
//   * Instead of `getParent` we use `getContext` here. This trick allows for funny things like creating
//     a code fragment in a temporary file and attaching it to some existing file. See the usages of
//     [RsCodeFragmentFactory]

private val LOG: Logger = Logger.getInstance("org.rust.lang.core.resolve.NameResolution")

fun processDotExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    context: RsElement,
    processor: RsResolveProcessorBase<DotExprResolveVariant>
): Boolean {
    if (processFieldExprResolveVariants(lookup, receiverType, processor)) return true
    if (processMethodDeclarationsWithDeref(lookup, receiverType, context, processor)) return true

    return false
}

fun processFieldExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    originalProcessor: RsResolveProcessorBase<FieldResolveVariant>
): Boolean {
    val autoderef = lookup.coercionSequence(receiverType)
    for (ty in autoderef) {
        if (ty !is TyAdt || ty.item !is RsStructItem) continue
        val processor = originalProcessor.wrapWithMapper { it: ScopeEntry ->
            FieldResolveVariant(it.name, it.element, ty, autoderef.steps(), autoderef.obligations())
        }
        if (processFieldDeclarations(ty.item, processor)) {
            return true
        }
    }

    return false
}

fun processStructLiteralFieldResolveVariants(
    field: RsStructLiteralField,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val resolved = field.parentStructLiteral.path.reference?.deepResolve() as? RsFieldsOwner
    if (resolved != null && processFieldDeclarations(resolved, processor)) return true
    if (!isCompletion && field.expr == null) {
        processNestedScopesUpwards(field, VALUES, processor)
    }
    return false
}

fun processStructPatternFieldResolveVariants(
    field: RsPatFieldFull,
    processor: RsResolveProcessor
): Boolean {
    val resolved = field.parentStructPattern.path.reference?.deepResolve()
    val resolvedStruct = resolved as? RsFieldsOwner ?: return false
    return processFieldDeclarations(resolvedStruct, processor)
}

fun processMethodCallExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    context: RsElement,
    processor: RsMethodResolveProcessor
): Boolean =
    processMethodDeclarationsWithDeref(lookup, receiverType, context, processor)

/**
 * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
 *
 *  ```
 *  // foo.rs
 *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir if `foo` is crate root,
 *               // `foo/bar.rs` or `foo/bar/mod.rs` otherwise
 *
 *  pub mod nested {
 *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs` if `foo` is crate root,
 *                   // `foo/nested/baz.rs` or `foo/nested/baz/mod.rs` otherwise
 *  }
 *
 *  ```
 *
 *  | A module without a body is loaded from an external file, by default with the same name as the module,
 *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
 *  | from a subdirectory path that mirrors the module hierarchy.
 *
 * Reference:
 *      https://github.com/rust-lang-nursery/reference/blob/master/src/items/modules.md
 */
fun processModDeclResolveVariants(modDecl: RsModDeclItem, processor: RsResolveProcessor): Boolean {
    val psiMgr = PsiManager.getInstance(modDecl.project)
    val containingMod = modDecl.containingMod

    val ownedDirectory = containingMod.getOwnedDirectory()
    val contextualFile = modDecl.contextualFile
    val originalFileOriginal = contextualFile.originalFile.virtualFile
    val inModRs = contextualFile.name == RsConstants.MOD_RS_FILE
    val inCrateRoot = lazy(LazyThreadSafetyMode.NONE) { containingMod.isCrateRoot }

    val explicitPath = modDecl.pathAttribute
    if (explicitPath != null) {
        // Explicit path is relative to:
        // * owned directory when module declared in inline module
        // * parent of module declaration otherwise
        val dir = if (containingMod is RsFile) {
            contextualFile.parent
        } else {
            ownedDirectory
        } ?: return false
        val vFile = dir.virtualFile.findFileByRelativePath(FileUtil.toSystemIndependentName(explicitPath))
            ?: return false
        val mod = psiMgr.findFile(vFile)?.rustFile ?: return false

        val name = modDecl.name ?: return false
        return processor.process(name, TYPES, mod)
    }
    if (ownedDirectory == null) return false
    if (modDecl.isLocal) return false

    val modDeclName = modDecl.referenceName

    fun fileName(rawName: String): String {
        val fileName = FileUtil.getNameWithoutExtension(rawName)
        // Handle case-insensitive filesystem (windows)
        return if (modDeclName.equals(fileName, ignoreCase = true)) modDeclName else fileName
    }

    val (dirs, files) = ownedDirectory.virtualFile.children.partition { it.isDirectory }

    for (vFile in files) {
        val rawFileName = vFile.name
        if (vFile == originalFileOriginal || rawFileName == RsConstants.MOD_RS_FILE) continue
        if (processor.lazy(fileName(rawFileName), TYPES) { psiMgr.findFile(vFile)?.rustFile }) return true
    }

    for (vDir in dirs) {
        val mod = vDir.findChild(RsConstants.MOD_RS_FILE)
        if (mod != null) {
            if (processor.lazy(vDir.name, TYPES) { psiMgr.findFile(mod)?.rustFile }) return true
        }

        // We shouldn't search possible module files in subdirectories
        // if module declaration is located in `mod.rs`
        if (inModRs) {
            ModRsFile.hit()
            continue
        }

        // Submodule file of crate root (for example, `mod foo;` in `src/main.rs`)
        // can be located in the same directory with parent module (i.e. in `src/foo.rs`)
        // or in `mod.rs` of subdirectory of crate root dir (i.e. in `src/foo/mod.rs`)
        // Both cases are handled above
        if (inCrateRoot.value) {
            CrateRootModule.hit()
            continue
        }

        if (vDir.name == containingMod.modName) {
            for (vFile in vDir.children) {
                if (vFile.isDirectory) continue
                val rawFileName = vFile.name
                if (rawFileName == RsConstants.MOD_RS_FILE) continue
                if (processor.lazy(fileName(rawFileName), TYPES) { psiMgr.findFile(vFile)?.rustFile }) return true
            }
        }
    }

    return false
}

/**
 * Variants from [processExternCrateResolveVariants] + all renamings `extern crate name as alias;`
 * See https://doc.rust-lang.org/reference/names/preludes.html#extern-prelude
 */
fun processExternPreludeResolveVariants(ctx: PathResolutionContext, processor: RsResolveProcessor): Boolean {
    val (project, defMap) = ctx.containingModInfo ?: return false
    for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithNames(processor.names)) {
        val externCrateRoot = externCrateDefMap.rootAsRsMod(project) ?: continue
        if (processor.process(name, TYPES, externCrateRoot)) return true
    }
    return false
}

/** Processes dependencies crates (specified in Cargo.toml) */
fun processExternCrateResolveVariants(
    element: RsElement,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): Boolean = processExternCrateResolveVariants(element, isCompletion, !isCompletion, processor)

fun processExternCrateResolveVariants(
    element: RsElement,
    isCompletion: Boolean,
    withSelf: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val crate = element.containingCrate

    val visitedDeps = mutableSetOf<String>()
    fun processPackage(crate: Crate, dependencyName: String): Boolean {
        val isDependencyOrWorkspace = crate.origin == PackageOrigin.DEPENDENCY || crate.origin == PackageOrigin.WORKSPACE
        if (isCompletion && !isDependencyOrWorkspace) return false

        if (crate.origin == PackageOrigin.STDLIB && dependencyName in visitedDeps) return false
        visitedDeps.add(dependencyName)

        return processor.lazy(dependencyName, TYPES) {
            crate.rootModFile?.toPsiFile(element.project)?.rustFile
        }
    }

    if (withSelf) {
        if (processor.lazy("self", TYPES) { crate.rootMod }) return true
    }
    val explicitDepsFirst = crate.dependenciesWithCyclic.sortedBy {
        when (it.crate.origin) {
            PackageOrigin.WORKSPACE,
            PackageOrigin.DEPENDENCY -> 0
            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> 1
        }
    }
    for (dependency in explicitDepsFirst) {
        if (processPackage(dependency.crate, dependency.normName)) return true
    }
    return false
}

fun findDependencyCrateByNamePath(context: RsElement, path: RsPath): RsFile? {
    return when (val referenceName = path.referenceName) {
        null -> null
        MACRO_DOLLAR_CRATE_IDENTIFIER -> path.resolveDollarCrateIdentifier()?.rootMod
        "crate" -> context.crateRoot as? RsFile
        else -> findDependencyCrateByName(context, referenceName)
    }
}

fun findDependencyCrateByName(context: RsElement, name: String): RsFile? {
    return pickFirstResolveVariant(name) {
        processExternCrateResolveVariants(context, false, it)
    } as? RsFile
}

fun processPathResolveVariants(
    lookup: ImplLookup?,
    path: RsPath,
    isCompletion: Boolean,
    processAssocItems: Boolean,
    processor: RsResolveProcessor,
): Boolean {
    val ctx = PathResolutionContext(path, isCompletion, processAssocItems, lookup)
    val pathKind = ctx.classifyPath(path)
    return processPathResolveVariants(ctx, pathKind, processor)
}

fun processPathResolveVariants(ctx: PathResolutionContext, pathKind: RsPathResolveKind, processor: RsResolveProcessor): Boolean {
    return when (pathKind) {
        is RsPathResolveKind.UnqualifiedPath -> {
            val stop = processSelfSuperCrate(pathKind.ns, ctx, processor)
            if (stop) return true
            processNestedScopesUpwards(
                ctx.context,
                pathKind.ns,
                ctx,
                processor.withIgnoringSecondaryCSelf()
            )
        }
        is RsPathResolveKind.QualifiedPath -> {
            processQualifiedPathResolveVariants(
                ctx,
                ctx.isCompletion,
                pathKind.ns,
                pathKind.qualifier,
                pathKind.path,
                pathKind.parent,
                processor
            )
        }
        is RsPathResolveKind.ExplicitTypeQualifiedPath -> {
            processExplicitTypeQualifiedPathResolveVariants(ctx, pathKind.ns, pathKind.typeQual, processor)
        }
        is RsPathResolveKind.MacroDollarCrateIdentifier -> {
            processMacroDollarCrateResolveVariants(pathKind.path, processor)
        }
        is RsPathResolveKind.CrateRelativePath -> {
            if (!pathKind.hasColonColon) {
                val stop = processSelfSuperCrate(pathKind.ns, ctx, processor)
                if (stop) return true
            }
            val crateRoot = ctx.crateRoot
            if (crateRoot != null) {
                processItemDeclarationsInMod(crateRoot, pathKind.ns, processor, withPrivateImports = true)
            } else {
                false
            }
        }
        is RsPathResolveKind.ExternCratePath -> {
            processExternPreludeResolveVariants(ctx, processor)
        }
        is RsPathResolveKind.AssocTypeBindingPath -> {
            processAssocTypeVariants(pathKind.parent, processor)
        }
    }
}

private fun RsResolveProcessor.withIgnoringSecondaryCSelf(): RsResolveProcessor {
    val names = names
    if (names != null && "Self" !in names) return this

    var hasSelfItem = false

    return wrapWithFilter {
        if (it.name == "Self") {
            if (hasSelfItem) {
                false
            } else {
                hasSelfItem = true
                true
            }
        } else {
            true
        }
    }
}

/**
 * foo::bar
 * |    |
 * |    [path]
 * [qualifier]
 */
private fun processQualifiedPathResolveVariants(
    ctx: PathResolutionContext?,
    isCompletion: Boolean,
    ns: Set<Namespace>,
    qualifier: RsPath,
    path: RsPath,
    parent: PsiElement?,
    processor: RsResolveProcessor
): Boolean {
    val resolvedQualifier = qualifier.reference?.advancedResolve()
    val primitiveType = TyPrimitive.fromPath(qualifier, checkResolve = false)

    val prevScope = hashMapOf<String, Set<Namespace>>()

    if (resolvedQualifier != null) {
        val shadowingProcessor = if (primitiveType != null) {
            processor.wrapWithBeforeProcessingHandler { e ->
                if (processor.acceptsName(e.name)) {
                    prevScope[e.name] = e.namespaces
                }
            }
        } else {
            processor
        }

        val result = processQualifiedPathResolveVariants1(
            ctx,
            isCompletion,
            ns,
            qualifier,
            resolvedQualifier,
            path,
            parent,
            shadowingProcessor
        )
        if (result) return true
    }

    // `ctx` is `null` only in the case of macro call path, so  processing associated members makes no sense
    if (primitiveType != null && ctx != null) {
        val result = processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
            processTypeQualifiedPathResolveVariants(ctx, shadowingProcessor, ns, primitiveType)
        }
        if (result) return true
    }
    return false
}

private fun processQualifiedPathResolveVariants1(
    ctx: PathResolutionContext?,
    isCompletion: Boolean,
    ns: Set<Namespace>,
    qualifier: RsPath,
    resolvedQualifier: BoundElement<RsElement>,
    path: RsPath,
    parent: PsiElement?,
    processor: RsResolveProcessor
): Boolean {
    val (base, subst) = resolvedQualifier

    if (parent is RsUseSpeck && path.path == null) {
        SelfInGroup.hit()
        if (processor.process("self", TYPES, base)) return true
    }

    if (base is RsMod) {
        val result = processor.lazy("super", TYPES) {
            // `super` is allowed only after `self` and `super`
            // so we add `super` in completion only when it produces valid path
            if (!isCompletion || isSuperChain(qualifier)) {
                base.`super`
            } else {
                null
            }
        }
        if (result) return true

        val containingMod = ctx?.containingMod ?: path.containingMod
        if (Namespace.Macros in ns) {
            if (processMacros(base, processor, path)) return true
        }

        // Proc macro crates are not allowed to export anything but procedural macros,
        // and all possible macro exports are collected above. However, when resolve
        // happens inside proc macro crate itself, all items are allowed
        val baseModContainingCrate = base.containingCrate
        val resolveBetweenDifferentTargets = baseModContainingCrate != containingMod.containingCrate
        if (resolveBetweenDifferentTargets && baseModContainingCrate.kind.isProcMacro) {
            return false
        }
    }

    // `ctx` is `null` only in the case of macro call path, so processing anything but macros makes no sense
    if (ctx == null) return false

    val prevScope = hashMapOf<String, Set<Namespace>>()

    // Procedural macros definitions are functions, so they get added twice (once as macros, and once as items). To
    // avoid this, we exclude `MACROS` from passed namespaces
    val result1 = processWithShadowingAndUpdateScope(prevScope, ns - MACROS, processor) {
        if (parent is RsUseSpeck && base is RsEnumItem) {
            if (processEnumVariants(it, base)) return true
        }
        if (base is RsMod) {
            processItemDeclarationsInMod(
                base,
                ns - MACROS,
                it,
                withPrivateImports = withPrivateImports(qualifier, base)
            )
        } else {
            false
        }
    }
    if (result1) return true

    if (base is RsTraitItem && parent !is RsUseSpeck && !qualifier.hasCself) {
        if (processTraitRelativePath(BoundElement(base, subst), ns, processor)) return true
    } else if (base is RsTypeDeclarationElement && parent !is RsUseSpeck) { // Foo::<Bar>::baz
        val (baseTy, _) = ctx.implLookup.ctx.normalizeAssociatedTypesIn(if (qualifier.hasCself) {
            when (base) {
                // impl S { fn foo() { Self::bar() } }
                is RsImplItem -> base.typeReference?.rawType ?: TyUnknown
                is RsTraitItem -> TyTypeParameter.self(base)
                else -> TyUnknown
            }
        } else {
            val realSubst = if (qualifier.typeArgumentList == null && base is RsGenericDeclaration) {
                subst.substituteInValues(Substitution(
                    typeSubst = base.generics.associateWith { TyInfer.TyVar(it) },
                    constSubst = base.constGenerics.associateWith { CtInferVar(it) }
                ))
            } else {
                // If the path contains explicit type arguments `Foo::<_, Bar, _>::baz`
                // it means that all possible `TyInfer` has already substituted (with `_`)
                subst
            }
            base.declaredType.substitute(realSubst)
        })

        // Self-qualified type paths `Self::Item` inside impl items are restricted to resolve
        // to only members of the current impl or implemented trait or its parent traits
        val restrictedTraits = if (Namespace.Types in ns && base is RsImplItem && qualifier.hasCself) {
            NameResolutionTestmarks.SelfRelatedTypeSpecialCase.hit()
            base.implementedTrait?.getFlattenHierarchy()
        } else {
            null
        }

        if (processEnumVariantsWithShadowing(baseTy, prevScope, ns, processor)) return true

        if (!ctx.processAssocItems) return false

        val result2 = processWithShadowing(prevScope, ns, processor) {
            if (restrictedTraits != null) {
                processTypeAsTraitUFCSQualifiedPathResolveVariants(ns, baseTy, restrictedTraits, it)
            } else {
                processTypeQualifiedPathResolveVariants(ctx, it, ns, baseTy)
            }
        }
        if (result2) return true
    }
    return false
}

private fun processEnumVariantsWithShadowing(
    baseTy: Ty,
    prevScope: HashMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean {
    return if (baseTy is TyAdt && baseTy.item is RsEnumItem) {
        processWithShadowingAndUpdateScope(prevScope, ns, processor) {
            processEnumVariants(it, baseTy.item, baseTy.typeParameterValues)
        }
    } else {
        false
    }
}

private fun processEnumVariants(
    processor: RsResolveProcessor,
    item: RsEnumItem,
    subst: Substitution = emptySubstitution
): Boolean {
    return processAllWithSubst(item.variants, subst, ENUM_VARIANT_NS, processor)
}

/** `<T as Trait>::Item` or `<T>::Item` */
private fun processExplicitTypeQualifiedPathResolveVariants(
    ctx: PathResolutionContext,
    ns: Set<Namespace>,
    typeQual: RsTypeQual,
    processor: RsResolveProcessor
): Boolean {
    val trait = typeQual.traitRef?.resolveToBoundTrait()
        // TODO this is a hack to fix completion test `test associated type in explicit UFCS form`.
        // Looks like we should use getOriginalOrSelf during resolve
        ?.let { BoundElement(CompletionUtil.getOriginalOrSelf(it.element), it.subst) }
    val rawBaseTy = typeQual.typeReference.rawType
    return if (trait != null) {
        processTypeAsTraitUFCSQualifiedPathResolveVariants(ns, rawBaseTy, listOf(trait), processor)
    } else {
        val (baseTy, _) = ctx.implLookup.ctx.normalizeAssociatedTypesIn(rawBaseTy, 0)
        val prevScope = hashMapOf<String, Set<Namespace>>()
        if (processEnumVariantsWithShadowing(baseTy, prevScope, ns, processor)) return true
        processWithShadowing(prevScope, ns, processor) {
            processTypeQualifiedPathResolveVariants(ctx, it, ns, baseTy)
        }
    }
}

private fun processSelfSuperCrate(
    ns: Set<Namespace>,
    ctx: PathResolutionContext,
    processor: RsResolveProcessor
): Boolean {
    if (Namespace.Types in ns) {
        if (processor.lazy("self", TYPES) { ctx.containingMod }) return true
        if (processor.lazy("super", TYPES) { ctx.containingMod.`super` }) return true
        if (ctx.crateRoot != null && processor.process("crate", TYPES, ctx.crateRoot)) return true
    }
    return false
}

private fun processMacroDollarCrateResolveVariants(path: RsPath, processor: RsResolveProcessor): Boolean {
    return processor.lazy(MACRO_DOLLAR_CRATE_IDENTIFIER, TYPES) {
        path.resolveDollarCrateIdentifier()?.rootMod
    }
}

fun RsPath.resolveDollarCrateIdentifier(): Crate? {
    NameResolutionTestmarks.DollarCrateMagicIdentifier.hit()
    val dollarCrateSource = findMacroCallFromWhichLeafIsExpanded() ?: this
    return (dollarCrateSource.findMacroCallExpandedFromNonRecursive() as? RsMacroCall)
        ?.resolveToMacroAndGetContainingCrate()
}

private fun processTypeQualifiedPathResolveVariants(
    ctx: PathResolutionContext,
    processor: RsResolveProcessor,
    ns: Set<Namespace>,
    baseTy: Ty,
): Boolean {
    val lookup = ctx.implLookup
    val shadowingProcessor = processor.wrapWithFilter<AssocItemScopeEntry> { e ->
        if (e.element is RsTypeAlias && baseTy is TyTypeParameter && e.source is TraitImplSource.ExplicitImpl) {
            NameResolutionTestmarks.SkipAssocTypeFromImpl.hit()
            false
        } else {
            true
        }
    }
    val selfSubst = if (baseTy !is TyTraitObject) {
        mapOf(TyTypeParameter.self() to baseTy).toTypeSubst()
    } else {
        emptySubstitution
    }
    if (processAssociatedItemsWithSelfSubst(lookup, ctx.context, baseTy, ns, selfSubst, shadowingProcessor)) return true
    return false
}

/** For `Item` in `<T as Trait>::Item;`, process members of `Trait` */
private fun processTypeAsTraitUFCSQualifiedPathResolveVariants(
    ns: Set<Namespace>,
    baseTy: Ty,
    restrictedTraits: Collection<BoundElement<RsTraitItem>>,
    processor: RsResolveProcessor
): Boolean {
    val selfSubst = mapOf(TyTypeParameter.self() to baseTy).toTypeSubst()
    for (boundTrait in restrictedTraits) {
        val source = TraitImplSource.Trait(boundTrait.element)
        val subst = boundTrait.subst + selfSubst
        if (processTraitMembers(boundTrait.element, ns, subst, baseTy, source, processor)) return true
    }
    return false
}

/** `TraitName::foo` */
private fun processTraitRelativePath(
    baseBoundTrait: BoundElement<RsTraitItem>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean {
    for (boundTrait in baseBoundTrait.getFlattenHierarchy()) {
        val source = TraitImplSource.Trait(boundTrait.element)
        if (processTraitMembers(boundTrait.element, ns, boundTrait.subst, TyUnknown, source, processor)) return true
    }
    return false
}

private fun processTraitMembers(
    trait: RsTraitItem,
    ns: Set<Namespace>,
    subst: Substitution,
    selfTy: Ty,
    source: TraitImplSource.Trait,
    processor: RsResolveProcessor
): Boolean {
    val nsFilter = assocMembersNsFilter(ns) ?: return false
    for (item in trait.members?.expandedMembers.orEmpty()) {
        if (!nsFilter(item)) continue
        val name = item.name ?: continue
        val namespaces = assocMemberNamespaces(item)
        if (processor.process(AssocItemScopeEntry(name, item, namespaces, subst, selfTy, source))) return true
    }
    return false
}

fun processPatBindingResolveVariants(
    binding: RsPatBinding,
    isCompletion: Boolean,
    originalProcessor: RsResolveProcessor
): Boolean {
    if (binding.parent is RsPatField) {
        val parentPat = binding.parent.parent as RsPatStruct
        val patStruct = parentPat.path.reference?.deepResolve()
        if (patStruct is RsFieldsOwner) {
            if (processFieldDeclarations(patStruct, originalProcessor)) return true
            if (isCompletion) return false
        }
    }

    val processor = originalProcessor.wrapWithFilter { entry ->
        if (originalProcessor.acceptsName(entry.name)) {
            val element = entry.element
            val isConstant = element.isConstantLike
            val isPathOrDestructable = when (element) {
                is RsMod, is RsEnumItem, is RsEnumVariant, is RsStructItem -> true
                else -> false
            }
            isConstant || (isCompletion && isPathOrDestructable)
        } else {
            false
        }
    }
    return processNestedScopesUpwards(binding, if (isCompletion) TYPES_N_VALUES else VALUES, processor)
}

fun processLabelResolveVariants(label: RsLabel, processor: RsResolveProcessor, processBeyondLabelBarriers: Boolean = false): Boolean {
    val prevScope = hashMapOf<String, Set<Namespace>>()
    for (scope in label.contexts) {
        if (!processBeyondLabelBarriers && isLabelBarrier(scope)) return false
        if (scope is RsLabeledExpression) {
            val labelDecl = scope.labelDecl ?: continue
            val stop = processWithShadowingAndUpdateScope(prevScope, LIFETIMES, processor) {
                it.process(labelDecl, LIFETIMES)
            }
            if (stop) return true
        }
    }
    return false
}

private fun isLabelBarrier(scope: PsiElement): Boolean {
    return scope is RsLambdaExpr || scope is RsFunction || scope is RsConstant || scope is RsBlockExpr && (scope.isAsync || scope.isConst)
}

fun resolveLabelReference(element: RsLabel, processBeyondLabelBarriers: Boolean = false): List<RsElement> {
    return collectResolveVariants(element.referenceName) { processLabelResolveVariants(element, it, processBeyondLabelBarriers) }
}

fun processLifetimeResolveVariants(lifetime: RsLifetime, processor: RsResolveProcessor): Boolean {
    if (lifetime.isPredefined) return false
    loop@ for (scope in lifetime.contexts) {
        val lifetimeParameters = when (scope) {
            is RsGenericDeclaration -> scope.lifetimeParameters
            is RsWhereClause -> scope.wherePredList.mapNotNull { it.forLifetimes }.flatMap { it.lifetimeParameterList }
            is RsForInType -> scope.forLifetimes.lifetimeParameterList
            is RsPolybound -> scope.forLifetimes?.lifetimeParameterList.orEmpty()
            else -> continue@loop
        }
        if (processor.processAll(lifetimeParameters, LIFETIMES)) return true
    }

    return false
}

fun processLocalVariables(place: RsElement, originalProcessor: (RsPatBinding) -> Unit) {
    val processor = createProcessor { v ->
        val el = v.element
        if (el is RsPatBinding) originalProcessor(el)
    }
    val hygieneFilter = makeHygieneFilter(place)
    walkUp(place, { it is RsItemElement }) { cameFrom, scope ->
        processLexicalDeclarations(scope, cameFrom, VALUES, hygieneFilter, ItemProcessingMode.WITH_PRIVATE_IMPORTS, processor)
    }
}

/**
 * Resolves an absolute path.
 */
fun resolveStringPath(
    path: String,
    workspace: CargoWorkspace,
    project: Project,
    isStd: ThreeState = ThreeState.UNSURE
): Pair<RsNamedElement, CargoWorkspace.Package>? {
    val (pkgName, crateRelativePath) = splitAbsolutePath(path) ?: return null
    val pkg = workspace.findPackageByName(pkgName, isStd) ?: run {
        return if (isUnitTestMode) {
            // Allows to set a fake path for some item in tests via
            // lang attribute, e.g. `#[lang = "std::iter::Iterator"]`
            RsLangItemIndex.findLangItem(project, path)?.let { it to workspace.packages.first() }
        } else {
            null
        }
    }

    val el = pkg.targets.asSequence()
        .mapNotNull { RsCodeFragmentFactory(project).createCrateRelativePath(crateRelativePath, it) }
        .filter {
            val crateRoot = it.containingFile.context as RsFile
            val crateId = crateRoot.containingCrate.asNotFake?.id ?: return@filter false
            // ignore e.g. test/bench non-workspace crates
            project.defMapService.getOrUpdateIfNeeded(crateId) != null
        }
        .mapNotNull { it.reference?.resolve() }
        .filterIsInstance<RsNamedElement>()
        .firstOrNull() ?: return null
    return el to pkg
}

/** 'crate_name::mod1::mod2' -> ('crate_name', 'mod1::mod2') */
fun splitAbsolutePath(path: String): Pair<String, String>? {
    check(!path.startsWith("::"))
    val parts = path.split("::", limit = 2)
    if (parts.size != 2) return null
    return parts[0] to parts[1]
}

fun processMacroReferenceVariants(ref: RsMacroReference, processor: RsResolveProcessor): Boolean {
    val definition = ref.ancestorStrict<RsMacroCase>() ?: return false
    val simple = definition.macroPattern.descendantsOfType<RsMacroBinding>()
        .toList()

    return simple.any { processor.process(it, TYPES) }
}

fun processProcMacroResolveVariants(
    path: RsPath,
    originalProcessor: RsResolveProcessor,
    isCompletion: Boolean
): Boolean {
    val qualifier = path.qualifier

    val processor = originalProcessor.wrapWithFilter {
        it.element !is RsMacroDefinitionBase
    }
    return if (qualifier == null) {
        processMacroCallVariantsInScope(path, ignoreLegacyMacros = true, processor)
    } else {
        processMacroCallPathResolveVariants(path, isCompletion, processor)
    }
}

fun processDeriveTraitResolveVariants(element: RsPath, traitName: String, processor: RsResolveProcessor): Boolean {
    processNestedScopesUpwards(element, MACROS, processor)
    // FIXME: support custom items with the same name as known derivable traits (i.e. `foo::bar::Clone`)
    val knownDerive = KNOWN_DERIVABLE_TRAITS[traitName]?.findTrait(element.knownItems)
    return if (knownDerive != null) {
        processor.process(knownDerive, TYPES)
    } else {
        val traits = RsNamedElementIndex.findElementsByName(element.project, traitName)
            .filterIsInstance<RsTraitItem>()
        processor.processAll(traits, TYPES)
    }
}

fun processBinaryOpVariants(element: RsBinaryOp, operator: OverloadableBinaryOperator,
                            processor: RsResolveProcessor): Boolean {
    val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return false
    val rhsType = binaryExpr.right?.type ?: return false
    val lhsType = binaryExpr.left.type
    val lookup = ImplLookup.relativeTo(element)
    val function = lookup.findOverloadedOpImpl(lhsType, rhsType, operator)
        ?.expandedMembers.orEmpty()
        .functions
        .find { it.name == operator.fnName }
        ?: return false
    return processor.process(function, VALUES)
}

fun processAssocTypeVariants(element: RsAssocTypeBinding, processor: RsResolveProcessor): Boolean {
    val trait = element.parentPath?.reference?.resolve() as? RsTraitItem ?: return false
    return processAssocTypeVariants(trait, processor)
}

fun processAssocTypeVariants(trait: RsTraitItem, processor: RsResolveProcessor): Boolean {
    if (trait.associatedTypesTransitively.any { processor.process(it, TYPES) }) return true
    return false
}

fun processMacroCallPathResolveVariants(path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    val qualifier = path.qualifier
    return if (qualifier == null) {
        if (path.hasColonColon && path.isAtLeastEdition2018) return false
        if (isCompletion) {
            processMacroCallVariantsInScope(path, ignoreLegacyMacros = false, processor)
        } else {
            if (!path.cameFromMacroCall()) {
                // Handles `#[macro_export(local_inner_macros)]`
                // this "recursive" macro resolve should not be a problem because
                // 1. we resolve the macro from which [path] is expanded, so it can't run into infinite recursion
                // 2. we expand macros step-by-step, so the result of such resolution should be cached already
                val expandedFrom = path.findMacroCallExpandedFromNonRecursive() as? RsMacroCall
                expandedFrom
                    ?.resolveToMacroAndProcessLocalInnerMacros(processor)
                    ?.let { return it }
            }

            val resolved = pickFirstResolveEntry(path.referenceName) {
                processMacroCallVariantsInScope(path, ignoreLegacyMacros = false, it)
            }
            resolved?.let { processor.process(it) } ?: false
        }
    } else {
        val call = path.parent
        if (call is RsPossibleMacroCall && call.canBeMacroCall) {
            if (isCompletion) {
                /** Note: here we don't have to handle [MACRO_DOLLAR_CRATE_IDENTIFIER] */
                processQualifiedPathResolveVariants(null, isCompletion = true, MACROS, qualifier, path, call, processor)
            } else {
                val def = call.resolveToMacroUsingNewResolve() ?: return false
                val name = processor.names?.singleOrNull() ?: def.name ?: return false
                processor.process(name, MACROS, def)
            }
        } else {
            // Allowed only 1 or 2-segment paths: `foo!()` or `foo::bar!()`, but not foo::bar::baz!();
            if (qualifier.path != null) return false
            processMacrosExportedByCratePath(path, qualifier, processor)
        }
    }
}

private fun processMacrosExportedByCratePath(context: RsElement, crateName: RsPath, processor: RsResolveProcessor): Boolean {
    val crateRoot = findDependencyCrateByNamePath(context, crateName) ?: return false
    return processMacrosExportedByCrate(crateRoot, processor)
}

private fun processMacrosExportedByCrate(crateRoot: RsFile, processor: RsResolveProcessor): Boolean {
    val exportedMacros = exportedMacrosAsScopeEntries(crateRoot)
    return processAllScopeEntries(exportedMacros, processor)
}

fun processMacroCallVariantsInScope(
    path: RsPath,
    ignoreLegacyMacros: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val (result, usedNewResolve) = MacroResolver.processMacrosInLexicalOrderUpward(path, ignoreLegacyMacros, processor)
    if (result || usedNewResolve) return result

    val crateRoot = path.crateRoot as? RsFile ?: return false
    val prelude = implicitStdlibCrate(crateRoot)?.crateRoot ?: return false
    return processAllScopeEntries(exportedMacrosAsScopeEntries(prelude), processor)
}

private data class MacroResolveResult(val result: Boolean, val usedNewResolve: Boolean = false) {
    companion object {
        val True: MacroResolveResult = MacroResolveResult(result = true)
        val False: MacroResolveResult = MacroResolveResult(result = false)
    }
}

private class MacroResolver private constructor(
    private val processor: RsResolveProcessor,
    ignoreLegacyMacros: Boolean,
    private val path: RsPath,
) : RsVisitor() {
    private val visitor = MacroResolvingVisitor(reverse = true, ignoreLegacyMacros) {
        processor.process(it, MACROS)
    }

    private fun processMacrosInLexicalOrderUpward(startElement: PsiElement): MacroResolveResult {
        val result = processScopesInLexicalOrderUpward(startElement)
        if (result.result || result.usedNewResolve) return result

        if (processRemainedExportedMacros()) return MacroResolveResult.True

        val crateRoot = startElement.contextOrSelf<RsElement>()?.crateRoot as? RsFile
            ?: return MacroResolveResult.False
        return processAllScopeEntries(exportedMacrosAsScopeEntries(crateRoot), processor).toResult()
    }

    /**
     * In short, it processes left siblings, then left siblings of the parent (without parent itself) and so on
     * until root (file), then it goes up to module declaration of the file (`mod foo;`) and processes its
     * siblings, and so on until crate root
     */
    private fun processScopesInLexicalOrderUpward(startElement: PsiElement): MacroResolveResult {
        val stub = (startElement as? StubBasedPsiElement<*>)?.greenStub
        return if (stub != null) {
            stubBasedProcessScopesInLexicalOrderUpward(stub)
        } else {
            psiBasedProcessScopesInLexicalOrderUpward(startElement)
        }
    }

    private tailrec fun psiBasedProcessScopesInLexicalOrderUpward(element: PsiElement): MacroResolveResult {
        tryProcessAllMacrosUsingNewResolve(element)?.let { return it }

        for (e in element.leftSiblings) {
            if (visitor.processMacros(e)) return MacroResolveResult.True
        }

        // ```
        // //- main.rs
        // macro_rules! foo { ... }
        // bar! { foo!(); } // expands to `foo!();` macro call
        // ```
        // In such case, if we expanded `bar!` macro call to macro call `foo!` and then resolving
        // `foo` macro, both `parent` and `context` of `foo!` macro call are "main.rs" file.
        // But we want to process macro definition before `bar!` macro call, so we have to use
        // a macro call as a "parent"
        val expandedFrom = (element as? RsExpandedElement)?.expandedFrom
        if (expandedFrom is RsMacroCall && processExpandedFrom(expandedFrom)) return MacroResolveResult.True
        val context = expandedFrom ?: element.context ?: return MacroResolveResult.False
        return when {
            context is RsFile -> {
                val declaration = context.declaration ?: return MacroResolveResult.False
                processScopesInLexicalOrderUpward(declaration)
            }
            // Optimization. Let this function be tailrec if go up by real parent (in the same file)
            context != element.parent -> processScopesInLexicalOrderUpward(context)
            else -> psiBasedProcessScopesInLexicalOrderUpward(context)
        }
    }

    private tailrec fun stubBasedProcessScopesInLexicalOrderUpward(element: StubElement<*>): MacroResolveResult {
        tryProcessAllMacrosUsingNewResolve(element.psi)?.let { return it }

        val parentStub = element.parentStub ?: return MacroResolveResult.False
        val siblings = parentStub.childrenStubs
        val index = siblings.indexOf(element)
        check(index != -1) { "Can't find stub index" }
        val leftSiblings = siblings.subList(0, index)
        for (it in leftSiblings) {
            if (visitor.processMacros(it.psi)) return MacroResolveResult.True
        }
        // See comment in psiBasedProcessScopesInLexicalOrderUpward
        val expandedFrom = (element.psi as? RsExpandedElement)?.expandedFrom
        if (expandedFrom is RsMacroCall && processExpandedFrom(expandedFrom)) return MacroResolveResult.True
        val parentPsi = expandedFrom ?: parentStub.psi
        return when {
            parentPsi is RsFile -> {
                val declaration = parentPsi.declaration ?: return MacroResolveResult.False
                processScopesInLexicalOrderUpward(declaration)
            }
            // Optimization. Let this function be tailrec if go up by stub parent
            parentPsi != parentStub.psi -> processScopesInLexicalOrderUpward(parentPsi)
            else -> stubBasedProcessScopesInLexicalOrderUpward(parentStub)
        }
    }

    /**
     * Optimization: macros often invoke themselves recursively.
     * If the expansion of `foo` macro contains `foo` macro call, it means:
     * 1. it's the same macro as a calling one
     * 2. name resolution result of calling macro should be already cached
     *
     * So we can just get this result from the cache directly and avoid expensive name resolution in such cases
     */
    private fun processExpandedFrom(expandedFrom: RsMacroCall): Boolean {
        if (expandedFrom.path.qualifier == null) {
            val resolved = (expandedFrom.path.reference as? RsMacroPathReferenceImpl)?.resolveIfCached()
                ?: return false
            if (processor.process(expandedFrom.macroName, MACROS, resolved)) return true
        }
        return false
    }

    /** Try using new resolve if [element] is top-level item or expanded from top-level macro call. */
    private fun tryProcessAllMacrosUsingNewResolve(element: PsiElement): MacroResolveResult? {
        if (element !is RsElement) return null
        val scope = element.context as? RsItemsOwner ?: return null // we are interested only in top-level elements
        val result = processMacros(scope, processor, path)
        if (scope !is RsMod && !result) return null  // we should search in parent scopes
        return result.toResult(usedNewResolve = true)
    }

    private fun processRemainedExportedMacros(): Boolean {
        return visitor.useItems.any { useItem ->
            processAllScopeEntries(collectMacrosImportedWithUseItem(useItem, visitor.exportingMacrosCrates), processor)
        }
    }

    companion object {
        fun processMacrosInLexicalOrderUpward(
            path: RsPath,
            ignoreLegacyMacros: Boolean,
            processor: RsResolveProcessor
        ): MacroResolveResult {
            return MacroResolver(processor, ignoreLegacyMacros, path).processMacrosInLexicalOrderUpward(path)
        }

        private fun Boolean.toResult(usedNewResolve: Boolean = false): MacroResolveResult =
            MacroResolveResult(this, usedNewResolve)
    }
}

private class MacroResolvingVisitor(
    private val reverse: Boolean,
    private val ignoreLegacyMacros: Boolean,
    private val processor: (RsNamedElement) -> Boolean
) : RsVisitor() {
    val exportingMacrosCrates = mutableMapOf<String, RsFile>()
    val useItems = mutableListOf<RsUseItem>()
    private var visitorResult: Boolean = false

    fun processMacros(element: PsiElement): Boolean =
        if (element is RsElement) processMacros(element) else false

    private fun processMacros(item: RsElement): Boolean {
        item.accept(this)
        return visitorResult
    }

    override fun visitMacro(item: RsMacro) {
        if (ignoreLegacyMacros) return
        visitorResult = processor(item)
    }

    override fun visitModItem(item: RsModItem) {
        if (ignoreLegacyMacros) return
        if (item.hasMacroUse && item.existsAfterExpansionSelf) {
            val elements = visibleMacros(item)
            visitorResult = processAll(if (reverse) elements.asReversed() else elements, processor)
        }
    }

    override fun visitModDeclItem(item: RsModDeclItem) {
        if (ignoreLegacyMacros) return
        if (item.hasMacroUse && item.existsAfterExpansionSelf) {
            val mod = item.reference.resolve() as? RsMod ?: return
            val elements = visibleMacros(mod)
            visitorResult = processAll(if (reverse) elements.asReversed() else elements, processor)
        }
    }

    override fun visitExternCrateItem(item: RsExternCrateItem) {
        if (!item.existsAfterExpansionSelf) return
        val mod = item.reference.resolve() as? RsFile ?: return
        if (item.hasMacroUse) {
            // If extern crate has `#[macro_use]` attribute
            // we can use all exported macros from the corresponding crate
            visitorResult = processAll(exportedMacros(mod), processor)
        } else {
            // otherwise we can use only reexported macros
            val reexportedMacros = reexportedMacros(item)
            if (reexportedMacros != null) {
                // via #[macro_reexport] attribute (old way)
                visitorResult = processAll(reexportedMacros, processor)
            } else {
                // or from `use` items (new way)
                exportingMacrosCrates[item.nameWithAlias] = mod
            }
        }
    }

    override fun visitUseItem(item: RsUseItem) {
        // Cfg attributes are evaluated later
        useItems += item
    }

    companion object {
        private fun visibleMacros(scope: RsItemsOwner): List<RsNamedElement> =
            CachedValuesManager.getCachedValue(scope) {
                val visibleMacros = mutableListOf<RsNamedElement>()
                val visitor = MacroResolvingVisitor(reverse = false, ignoreLegacyMacros = false) {
                    visibleMacros.add(it)
                    false
                }

                for (item in scope.itemsAndMacros) {
                    visitor.processMacros(item)
                }

                CachedValueProvider.Result.create(visibleMacros, scope.rustStructureOrAnyPsiModificationTracker)
            }

        private fun processAll(elements: Collection<RsNamedElement>, processor: (RsNamedElement) -> Boolean): Boolean =
            elements.any { processor(it) }
    }
}

private fun exportedMacros(scope: RsFile): List<RsNamedElement> =
    exportedMacrosAsScopeEntries(scope).mapNotNull { it.element as? RsNamedElement }

/**
 * We need `[ScopeEntry]` because custom derive procedural macros definitions have name different from the name of the
 * defined macro:
 *
 * ```
 * #[proc_macro_derive(ProcMacroName)]
 * pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
 * ```
 *
 * The definition function has name `example_proc_macro`, but the defined macro has name `ProcMacroName`.
 */
private fun exportedMacrosAsScopeEntries(scope: RsFile): List<ScopeEntry> {
    if (!scope.isCrateRoot) {
        LOG.warn("`${scope.virtualFile}` should be crate root")
        return emptyList()
    }
    return CachedValuesManager.getCachedValue(scope, EXPORTED_KEY) {
        val macros = exportedMacrosInternal(scope)
        CachedValueProvider.Result.create(macros, scope.rustStructureOrAnyPsiModificationTracker)
    }
}

private val EXPORTED_KEY: Key<CachedValue<List<ScopeEntry>>> = Key.create("EXPORTED_KEY")

private fun exportedMacrosInternal(scope: RsFile): List<ScopeEntry> {
    // proc-macro crates are allowed to export only procedural macros.
    if (scope.containingCrate.kind.isProcMacro) {
        return scope.stubChildrenOfType<RsFunction>().mapNotNull { asProcMacroDefinition(it) }
    }

    val allExportedMacros = RsMacroIndex.allExportedMacros(scope.project)
    return buildList {
        addAll(allExportedMacros[scope]?.toScopeEntries().orEmpty())

        val exportingMacrosCrates = mutableMapOf<String, RsMod>()

        val externCrates = scope.stubChildrenOfType<RsExternCrateItem>()
        for (item in externCrates) {
            if (!item.existsAfterExpansionSelf) continue
            val reexportedMacros = reexportedMacros(item)
            if (reexportedMacros != null) {
                addAll(reexportedMacros.toScopeEntries())
            } else {
                exportingMacrosCrates[item.nameWithAlias] = item.reference.resolve() as? RsMod ?: continue
            }
        }

        val implicitStdlibCrate = implicitStdlibCrate(scope)
        if (implicitStdlibCrate != null) {
            exportingMacrosCrates[implicitStdlibCrate.name] = implicitStdlibCrate.crateRoot
        }

        if (exportingMacrosCrates.isNotEmpty()) {
            for (useItem in scope.stubChildrenOfType<RsUseItem>()) {
                // only public use items can reexport macros
                if (!useItem.isPublic) continue
                addAll(collectMacrosImportedWithUseItem(useItem, exportingMacrosCrates))
            }
        }
    }
}

private fun asProcMacroDefinition(item: RsFunction): ScopeEntry? {
    val macroName = item.procMacroName ?: return null
    return SimpleScopeEntry(macroName, item, MACROS)
}

private fun List<RsMacro>.toScopeEntries(): List<ScopeEntry> =
    mapNotNull { item -> item.name?.let { SimpleScopeEntry(it, item, MACROS) } }

/**
 * Returns list of re-exported macros via `[macro_reexport]` attribute from given extern crate
 * or null if extern crate item doesn't have `[macro_reexport]` attribute
 */
@Suppress("KDocUnresolvedReference")
private fun reexportedMacros(item: RsExternCrateItem): List<RsMacro>? {
    val macroReexportAttr = item.findOuterAttr("macro_reexport") ?: return null
    val exportingMacroNames = macroReexportAttr
        .metaItem
        .metaItemArgs
        ?.metaItemList
        ?.mapNotNull { it.name }
        ?: return emptyList()
    val mod = item.reference.resolve() as? RsFile ?: return emptyList()
    val nameToExportedMacro = exportedMacros(mod).mapNotNull {
        if (it !is RsMacro) return@mapNotNull null
        val name = it.name ?: return@mapNotNull null
        name to it
    }.toMap()
    return exportingMacroNames.mapNotNull { nameToExportedMacro[it] }
}

private fun collectMacrosImportedWithUseItem(
    useItem: RsUseItem,
    exportingMacrosCrates: Map<String, RsMod>
): List<ScopeEntry> {
    // We don't want to perform path resolution during macro resolve (because it can recursively perform
    // macro resolve and so be slow and incorrect).
    // We assume that macro can only be imported by 2-segment path (`foo::bar`), where the first segment
    // is a name of the crate (may be aliased) and the second segment is the name of the macro.
    val root = useItem.useSpeck ?: return emptyList()
    val twoSegmentPaths = collect2segmentPaths(root)

    // Check cfg only if there are paths we interested in
    if (twoSegmentPaths.isEmpty() || !useItem.existsAfterExpansionSelf) return emptyList()

    return buildList {
        for ((crateName, macroName) in twoSegmentPaths) {
            val crateRoot = exportingMacrosCrates[crateName.referenceName ?: continue] as? RsFile
                ?: findDependencyCrateByNamePath(useItem, crateName)
                ?: continue
            val exportedMacros = exportedMacrosAsScopeEntries(crateRoot)
            addAll(if (macroName == null) {
                exportedMacros
            } else {
                exportedMacros.filter { it.name == macroName }
            })
        }
    }
}

/** Represents a path of 2 segments `foo::bar`. For wildcard `foo::*` path [rightSegment] is null */
private data class TwoSegmentPath(val leftSegment: RsPath, val rightSegment: String?)

/** For given `use foo::{bar, baz::quux, spam::{eggs}}` return the only `[foo::bar]`. */
private fun collect2segmentPaths(rootSpeck: RsUseSpeck): List<TwoSegmentPath> {
    val result = mutableListOf<TwoSegmentPath>()

    fun go(speck: RsUseSpeck) {
        val starImport = speck.isStarImport
        val path = speck.path
        val qualifier = path?.qualifier ?: speck.qualifier
        val group = speck.useGroup
        if (path != null && qualifier != null && qualifier.qualifier != null) return
        val firstSegment = qualifier ?: path
        val lastSegment = path ?: qualifier
        if (firstSegment != null && firstSegment.kind != PathKind.IDENTIFIER) return
        when {
            group == null && firstSegment != null && (path != null || starImport) -> {
                if (firstSegment != lastSegment && starImport) return
                val rightSegment = if (starImport) {
                    null
                } else {
                    path?.referenceName ?: return
                }
                result += TwoSegmentPath(firstSegment, rightSegment)
            }
            group != null && firstSegment == lastSegment -> {
                group.useSpeckList.forEach { go(it) }
            }
        }
    }
    go(rootSpeck)

    return result
}

private fun processFieldDeclarations(struct: RsFieldsOwner, processor: RsResolveProcessor): Boolean =
    struct.fields.any { field ->
        val name = field.name ?: return@any false
        processor.process(name, VALUES, field)
    }

private fun processMethodDeclarationsWithDeref(
    lookup: ImplLookup,
    receiver: Ty,
    context: RsElement,
    processor: RsMethodResolveProcessor
): Boolean {
    return lookup.coercionSequence(receiver).withPrevious().withIndex().any { (i, tyWithPrev) ->
        val (ty, prevTy) = tyWithPrev
        val tyIsSuitableForMethodCall = ty != TyUnknown && ty !is TyInfer.TyVar
        val withAutoBorrow = (prevTy !is TyReference || prevTy.mutability == MUTABLE) && tyIsSuitableForMethodCall
        val withAutoBorrowMut = (prevTy !is TyReference || prevTy.mutability == IMMUTABLE) && tyIsSuitableForMethodCall
        val methodProcessor = processor.wrapWithNonNullMapper<MethodResolveVariant, AssocItemScopeEntry> { e ->
            val element = e.element as? RsFunction ?: return@wrapWithNonNullMapper null

            // We intentionally use `hasSelfParameters` instead of `isMethod` because we already know that
            // it is an associated item and so if it is a function with self parameter - it is a method.
            // Also, this place is very hot and `hasSelfParameters` is cheaper than `isMethod`
            if (!element.hasSelfParameters) return@wrapWithNonNullMapper null

            val autoBorrow = e.selfTy is TyReference && e.selfTy.referenced === ty
            if (!autoBorrow || element.selfParameter?.isRef == false) {
                MethodResolveVariant(e.name, element, e.selfTy, i, e.source)
            } else {
                null
            }
        }
        processAssociatedItems(lookup, ty, VALUES, context, methodProcessor)
            || withAutoBorrow && processAssociatedItems(lookup, TyReference(ty, IMMUTABLE), VALUES, context, methodProcessor)
            || withAutoBorrowMut && processAssociatedItems(lookup, TyReference(ty, MUTABLE), VALUES, context, methodProcessor)
    }
}

private fun processAssociatedItems(
    lookup: ImplLookup,
    type: Ty,
    ns: Set<Namespace>,
    context: RsElement,
    processor: RsResolveProcessorBase<AssocItemScopeEntry>
): Boolean {
    val nsFilter = assocMembersNsFilter(ns) ?: return false

    val traitBounds = (type as? TyTypeParameter)?.let { lookup.getEnvBoundTransitivelyFor(it).toList() }
    val visitedInherent = hashMapOf<String, RsAbstractable>()
    val contextMod = lazy(LazyThreadSafetyMode.NONE) { context.containingMod }

    for (traitOrImpl in lookup.findImplsAndTraits(type)) {
        val isInherent = traitOrImpl.isInherent

        for ((name, member) in traitOrImpl.implAndTraitExpandedMembers.entriesWithNames(processor.names)) {
            if (!nsFilter(member)) continue

            // In Rust, inherent impl members (`impl Foo {}`) wins over trait impl members (`impl T for Foo {}`).
            // Note that `findImplsAndTraits` returns ordered sequence: inherent impls are placed to the head
            if (isInherent) {
                visitedInherent[name] = member
            } else if (visitedInherent[name]?.isVisibleFrom(contextMod.value) == true) {
                continue
            }

            val namespaces = assocMemberNamespaces(member)
            val result = if (traitBounds != null && traitOrImpl is TraitImplSource.TraitBound) {
                // Retrieve trait subst for associated type like
                // trait SliceIndex<T> { type Output; }
                // fn get<I: : SliceIndex<S>>(index: I) -> I::Output
                // Resulting subst will contain mapping T => S
                val bounds = traitBounds.filter { it.element == traitOrImpl.value }
                bounds.any { processor.process(AssocItemScopeEntry(name, member, namespaces, it.subst, type, traitOrImpl)) }
            } else {
                processor.process(AssocItemScopeEntry(name, member, namespaces, emptySubstitution, type, traitOrImpl))
            }
            if (result) return true
        }
    }
    return false
}

private fun assocMembersNsFilter(ns: Set<Namespace>): ((RsAbstractable) -> Boolean)? {
    return when {
        Namespace.Types in ns && Namespace.Values in ns -> {
            { true }
        }

        Namespace.Types in ns -> {
            { it is RsTypeAlias }
        }

        Namespace.Values in ns -> {
            { it !is RsTypeAlias }
        }

        else -> null
    }
}

private fun assocMemberNamespaces(member: RsAbstractable): Set<Namespace> {
    return when (member) {
        is RsTypeAlias -> TYPES
        else -> VALUES // RsFunction, RsConstant
    }
}

private fun Map<String, List<RsAbstractable>>.entriesWithNames(names: Set<String>?): Sequence<Pair<String, RsAbstractable>> {
    return if (names.isNullOrEmpty()) {
        entries.asSequence()
            .flatMap { (name, list) -> list.asSequence().map { name to it } }
    } else if (names.size == 1) {
        val single = names.single()
        val value = this[single]
        value.orEmpty().asSequence().map { single to it }
    } else {
        entries.asSequence()
            .flatMap { (name1, list) ->
                if (name1 in names) {
                    list.asSequence().map { name1 to it }
                } else {
                    emptySequence()
                }
            }
    }
}

private fun processAssociatedItemsWithSelfSubst(
    lookup: ImplLookup,
    context: RsElement,
    type: Ty,
    ns: Set<Namespace>,
    selfSubst: Substitution,
    processor: RsResolveProcessorBase<AssocItemScopeEntry>
): Boolean {
    return processAssociatedItems(lookup, type, ns, context, processor.wrapWithMapper {
        it.copy(subst = it.subst + selfSubst)
    })
}

private fun processLexicalDeclarations(
    scope: RsElement,
    cameFrom: PsiElement,
    ns: Set<Namespace>,
    hygieneFilter: (RsPatBinding) -> Boolean,
    ipm: ItemProcessingMode,
    processor: RsResolveProcessor
): Boolean {
    testAssert { cameFrom.context == scope }

    fun processPattern(pattern: RsPat, processor: RsResolveProcessor): Boolean {
        if (Namespace.Values !in ns) return false
        if (cameFrom == pattern) return false

        val alreadyProcessedNames = hashSetOf<String>()

        // Rust allows to defined several bindings in single pattern to the same name,
        // but they all must bind the same variables, hence we can inspect only the first one.
        // See https://github.com/rust-lang/rfcs/blob/master/text/2535-or-patterns.md
        val patternProcessor = processor.wrapWithFilter { e ->
            alreadyProcessedNames.add(e.name) // Process element if it is not in the set
        }

        return !processElementsWithMacros(pattern) { binding ->
           if (binding !is RsPatBinding) {
               return@processElementsWithMacros TreeStatus.VISIT_CHILDREN
           }

           val name = binding.name ?: return@processElementsWithMacros TreeStatus.SKIP_CHILDREN
           val result = patternProcessor.lazy(name, VALUES) {
               binding.takeIf { (binding.parent is RsPatField || !binding.isReferenceToConstant) && hygieneFilter(binding) }
           }

           if (result) {
               TreeStatus.ABORT
           } else {
               TreeStatus.SKIP_CHILDREN
           }
       }
    }

    fun processLetExprs(
        expr: RsExpr?,
        processor: RsResolveProcessor,
        prevScope: MutableMap<String, Set<Namespace>> = hashMapOf()
    ): Boolean {
        if (expr == null || expr == cameFrom) return false

        val shadowingProcessor = processor.wrapWithShadowingProcessorAndImmediatelyUpdateScope(prevScope, VALUES)

        fun process(expr: RsExpr): Boolean {
            when (expr) {
                cameFrom -> return false

                is RsLetExpr -> {
                    val pat = expr.pat ?: return false
                    return processPattern(pat, shadowingProcessor)
                }

                is RsBinaryExpr -> {
                    if (expr.right?.let { process(it) } == true) return true
                    return process(expr.left)
                }

                else -> return false
            }
        }


        return process(expr)
    }

    when (scope) {
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor, ipm = ipm)) return true
        }

        is RsTypeAlias -> {
            if (Namespace.Types in ns && processor.processAll(scope.typeParameters, TYPES)) return true
            if (Namespace.Values in ns && processor.processAll(scope.constParameters, VALUES)) return true
        }

        is RsStructItem,
        is RsEnumItem,
        is RsTraitOrImpl -> {
            scope as RsGenericDeclaration
            if (Namespace.Types in ns && processor.processAll(scope.typeParameters, TYPES)) return true
            if (Namespace.Values in ns && processor.processAll(scope.constParameters, VALUES)) return true
            if (processor.process("Self", TYPES_N_VALUES, scope)) return true
            if (scope is RsImplItem) {
                scope.traitRef?.let { traitRef ->
                    // really should be unnamed, but "_" is not a valid name in rust, so I think it's ok
                    if (processor.lazy("_", TYPES) { traitRef.resolveToTrait() }) return true
                }
            }
        }

        is RsFunction -> {
            if (Namespace.Types in ns && processor.processAll(scope.typeParameters, TYPES)) return true
            if (Namespace.Values in ns && processor.processAll(scope.constParameters, VALUES)) return true
            // XXX: `cameFrom is RsBlock` prevents switches to AST in cases like `fn foo(a: usize, b: [u8; SIZE])`.
            // Note that rustc really process them and show [E0435] on this: `fn foo(a: usize, b: [u8; a])`.
            if (Namespace.Values in ns && cameFrom is RsBlock) {
                val selfParam = scope.selfParameter
                if (selfParam != null && processor.process("self", VALUES, selfParam)) return true

                for (parameter in scope.valueParameters) {
                    val pat = parameter.pat ?: continue
                    if (processPattern(pat, processor)) return true
                }
            }
        }

        is RsBlock, is RsReplCodeFragment -> {
            // We want to filter out
            // all non strictly preceding let declarations.
            //
            // ```
            // let x = 92; // visible
            // let x = x;  // not visible
            //         ^ context.place
            // let x = 62; // not visible
            // ```
            val prevScope = hashMapOf<String, Set<Namespace>>()
            if (Namespace.Values in ns) {
                val shadowingProcessor = processor.wrapWithShadowingProcessorAndImmediatelyUpdateScope(prevScope, VALUES)

                val letDecls = mutableListOf<RsLetDecl>()
                val stmts = when (scope) {
                    is RsBlock -> scope.expandedStmtsAndTailExpr.first
                    is RsReplCodeFragment -> scope.stmtList
                    else -> emptyList()  // unreachable
                }
                for (stmt in stmts) {
                    if (cameFrom == stmt) break
                    if (stmt is RsLetDecl) {
                        letDecls.add(stmt)
                    }
                }

                for (let in letDecls.asReversed()) {
                    val pat = let.pat ?: continue
                    if (processPattern(pat, shadowingProcessor)) return true
                }
            }

            return processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                processItemDeclarations(scope as RsItemsOwner, ns, shadowingProcessor, ItemProcessingMode.WITH_PRIVATE_IMPORTS)
            }
        }

        is RsForExpr -> {
            if (scope.expr == cameFrom) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.pat
            if (pat != null && processPattern(pat, processor)) return true
        }

        is RsIfExpr -> {
            if (scope.block != cameFrom) return false
            val expr = scope.condition?.expr
            return processLetExprs(expr, processor)
        }
        is RsWhileExpr -> {
            if (scope.block != cameFrom) return false
            val expr = scope.condition?.expr
            return processLetExprs(expr, processor)
        }

        is RsBinaryExpr -> {
            if (scope.right != cameFrom) return false
            return processLetExprs(scope.left, processor)
        }

        is RsLambdaExpr -> {
            if (Namespace.Values !in ns) return false
            if (scope.expr != cameFrom) return false
            for (parameter in scope.valueParameters) {
                val pat = parameter.pat
                if (pat != null && processPattern(pat, processor)) return true
            }
        }

        is RsMatchArm -> {
            val guardExpr = scope.matchArmGuard?.expr
            if (guardExpr == null || scope.expr != cameFrom) return processPattern(scope.pat, processor)
            val prevScope = hashMapOf<String, Set<Namespace>>()
            val stop = processLetExprs(guardExpr, processor, prevScope)
            if (stop) return true
            return processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                processPattern(scope.pat, shadowingProcessor)
            }
        }
    }
    return false
}

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean = processNestedScopesUpwards(scopeStart, ns, null, processor)

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    ns: Set<Namespace>,
    ctx: PathResolutionContext?,
    processor: RsResolveProcessor
): Boolean {
    val hygieneFilter: (RsPatBinding) -> Boolean = if (scopeStart is RsPath && ns == VALUES) {
        makeHygieneFilter(scopeStart)
    } else {
        { true }
    }
    val prevScope = hashMapOf<String, Set<Namespace>>()
    return walkUp(scopeStart, { it is RsMod }) { cameFrom, scope ->
        if (scope !is RsMod) {
            processWithShadowingAndUpdateScope(prevScope, ns, processor) { shadowingProcessor ->
                val ipm = ItemProcessingMode.WITH_PRIVATE_IMPORTS
                processLexicalDeclarations(scope, cameFrom, ns, hygieneFilter, ipm, shadowingProcessor)
            }
        } else {
            // Optimization: use `RsModInfo` already stored in the `ctx` (or just calculate it if `ctx` is null)
            val modInfo = if (ctx != null) {
                ctx.getContainingModInfo(scope)
            } else {
                getModInfo(scope)
            } ?: return@walkUp false

            val stop = processWithShadowingAndUpdateScope(prevScope, ns, processor) { shadowingProcessor ->
                val ipm = ItemProcessingMode.WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES
                processItemDeclarationsUsingModInfo(scopeIsMod = true, modInfo, ns, shadowingProcessor, ipm)
            }
            if (stop) return@walkUp true

            val preludeModInfo = findPreludeUsingModInfo(modInfo)
            if (preludeModInfo != null) {
                return@walkUp processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                    processItemDeclarationsUsingModInfo(scopeIsMod = true, preludeModInfo, ns, shadowingProcessor, ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS)
                }
            }

            false
        }
    }
}

/**
 * Main part of the [hygiene](https://rust-lang.github.io/rustc-guide/macro-expansion.html#hygiene) implentation.
 */
private fun makeHygieneFilter(anchor: PsiElement): (RsPatBinding) -> Boolean {
    val anchorHygienicScope = if (!anchor.isExpandedFromMacro) {
        // This branch is needed to prevent AST access
        anchor.containingFile
    } else {
        (anchor.findMacroCallFromWhichLeafIsExpanded() ?: anchor).containingFile
    }.unwrapCodeFragments()

    return fun(binding: RsPatBinding): Boolean {
        val nameIdentifier = binding.nameIdentifier ?: return false
        val bindingHygienicScope =
            (nameIdentifier.findMacroCallFromWhichLeafIsExpanded() ?: nameIdentifier).containingFile
                .unwrapCodeFragments()
        return anchorHygienicScope == bindingHygienicScope
    }
}

private tailrec fun PsiFile.unwrapCodeFragments(): PsiFile {
    return if (this !is RsCodeFragment) {
        this
    } else {
        val context = context
        val file = if (context is PsiFile) context else context.containingFile
        file.unwrapCodeFragments()
    }
}

inline fun processWithShadowingAndUpdateScope(
    prevScope: MutableMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    f: (RsResolveProcessor) -> Boolean
): Boolean {
    val currScope = mutableMapOf<String, Set<Namespace>>()
    val shadowingProcessor = processor.wrapWithShadowingProcessorAndUpdateScope(prevScope, currScope, ns)
    return try {
        f(shadowingProcessor)
    } finally {
        prevScope.putAll(currScope)
    }
}

inline fun processWithShadowing(
    prevScope: Map<String, Set<Namespace>>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    f: (RsResolveProcessor) -> Boolean
): Boolean {
    val shadowingProcessor = processor.wrapWithShadowingProcessor(prevScope, ns)
    return f(shadowingProcessor)
}

fun processUnresolvedImports(context: RsElement, processor: (RsUseSpeck) -> Unit) {
    walkUp(context, { it is RsMod }) { _, scope ->
        if (scope !is RsItemsOwner) return@walkUp false
        for (import in scope.expandedItemsCached.imports) {
            if (!import.existsAfterExpansion) continue
            import.useSpeck?.forEachLeafSpeck { useSpeck ->
                if (useSpeck.isStarImport) return@forEachLeafSpeck
                if (useSpeck.path?.reference?.multiResolve()?.isEmpty() == true) {
                    processor(useSpeck)
                }
            }
        }
        false
    }
}

fun findPrelude(element: RsElement): RsMod? {
    val info = getModInfo(element.containingMod) ?: return null
    val prelude = info.defMap.prelude ?: return null
    return prelude.toRsMod(info).singleOrNull()
}

private fun findPreludeUsingModInfo(info: RsModInfo): RsModInfo? {
    val preludeModData = info.defMap.prelude ?: return null
    val preludeCrate = info.project.crateGraph.findCrateById(preludeModData.crate) ?: return null
    val preludeDefMap = info.defMap.getDefMap(preludeModData.crate) ?: return null
    return RsModInfo(info.project, preludeDefMap, preludeModData, preludeCrate)
}

// Implicit extern crate from stdlib
private fun implicitStdlibCrate(scope: RsFile): ImplicitStdlibCrate? {
    val (name, crateRoot) = when (scope.stdlibAttributes) {
        NO_CORE -> return null
        NO_STD -> CORE to scope.findDependencyCrateRoot(CORE)
        NONE -> STD to scope.findDependencyCrateRoot(STD)
    }
    return if (crateRoot == null) null else ImplicitStdlibCrate(name, crateRoot)
}

// There's already similar functions in TreeUtils, should use it
private fun walkUp(
    start: PsiElement,
    stopAfter: (RsElement) -> Boolean,
    stopAtFileBoundary: Boolean = true,
    processor: (cameFrom: PsiElement, scope: RsElement) -> Boolean
): Boolean {
    var cameFrom = start
    var scope = start.context as RsElement?
    while (scope != null) {
        ProgressManager.checkCanceled()
        if (processor(cameFrom, scope)) return true
        if (stopAfter(scope)) break
        cameFrom = scope
        scope = scope.context as? RsElement
        if (!stopAtFileBoundary) {
            if (scope == null && cameFrom is RsFile) {
                scope = cameFrom.`super`
            }
        }
    }
    return false
}

fun withPrivateImports(path: RsPath, resolvedPath: RsElement): Boolean {
    if (path.basePath().kind == PathKind.CRATE) {
        return resolvedPath is RsMod && resolvedPath in path.containingMod.superMods
    }
    return isSuperChain(path)
}

private fun isSuperChain(path: RsPath): Boolean {
    val qualifier = path.qualifier
    val pathKind = path.kind
    return (pathKind == PathKind.SUPER || pathKind == PathKind.SELF) &&
        (qualifier == null || isSuperChain(qualifier))
}


object NameResolutionTestmarks {
    object DollarCrateMagicIdentifier : Testmark()
    object SelfInGroup : Testmark()
    object CrateRootModule : Testmark()
    object ModRsFile : Testmark()
    object SelfRelatedTypeSpecialCase : Testmark()
    object SkipAssocTypeFromImpl : Testmark()
    object UpdateDefMapsForAllCratesWhenFindingModData : Testmark()
    object TypeAliasToImpl : Testmark()
}

private data class ImplicitStdlibCrate(val name: String, val crateRoot: RsFile)

