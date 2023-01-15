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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.RsConstants
import org.rust.lang.core.CompilerFeature.Companion.IN_BAND_LIFETIMES
import org.rust.lang.core.FeatureAvailability
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
import org.rust.stdext.*

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
): ShouldStop {
    return processFieldExprResolveVariants(lookup, receiverType, processor).and {
        processMethodDeclarationsWithDeref(lookup, receiverType, context, processor)
    }
}

fun processFieldExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    originalProcessor: RsResolveProcessorBase<FieldResolveVariant>
): ShouldStop {
    val autoderef = lookup.coercionSequence(receiverType)
    return autoderef.tryForEach { ty ->
        if (ty !is TyAdt || ty.item !is RsStructItem) return@tryForEach CONTINUE
        val processor = originalProcessor.wrapWithMapper { it: ScopeEntry ->
            FieldResolveVariant(it.name, it.element, ty, autoderef.steps())
        }
        processFieldDeclarations(ty.item, processor)
    }
}

fun processStructLiteralFieldResolveVariants(
    field: RsStructLiteralField,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): ShouldStop {
    val resolved = field.parentStructLiteral.path.reference?.deepResolve() as? RsFieldsOwner
    if (resolved != null) {
        processFieldDeclarations(resolved, processor).mapBreak { return BREAK }
    }
    if (!isCompletion && field.expr == null) {
        processNestedScopesUpwards(field, VALUES, processor)
    }
    return CONTINUE
}

fun processStructPatternFieldResolveVariants(
    field: RsPatFieldFull,
    processor: RsResolveProcessor
): ShouldStop {
    val resolved = field.parentStructPattern.path.reference?.deepResolve()
    val resolvedStruct = resolved as? RsFieldsOwner ?: return CONTINUE
    return processFieldDeclarations(resolvedStruct, processor)
}

fun processMethodCallExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    context: RsElement,
    processor: RsMethodResolveProcessor
): ShouldStop =
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
fun processModDeclResolveVariants(modDecl: RsModDeclItem, processor: RsResolveProcessor): ShouldStop {
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
        } ?: return CONTINUE
        val vFile = dir.virtualFile.findFileByRelativePath(FileUtil.toSystemIndependentName(explicitPath))
            ?: return CONTINUE
        val mod = psiMgr.findFile(vFile)?.rustFile ?: return CONTINUE

        val name = modDecl.name ?: return CONTINUE
        return processor.process(name, mod)
    }
    if (ownedDirectory == null) return CONTINUE
    if (modDecl.isLocal) return CONTINUE

    val modDeclName = modDecl.referenceName

    fun fileName(rawName: String): String {
        val fileName = FileUtil.getNameWithoutExtension(rawName)
        // Handle case-insensitive filesystem (windows)
        return if (modDeclName.equals(fileName, ignoreCase = true)) modDeclName else fileName
    }

    val (dirs, files) = ownedDirectory.virtualFile.children.partition { it.isDirectory }

    files.tryForEach { vFile ->
        val rawFileName = vFile.name
        if (vFile == originalFileOriginal || rawFileName == RsConstants.MOD_RS_FILE) return@tryForEach CONTINUE
        processor.lazy(fileName(rawFileName)) { psiMgr.findFile(vFile)?.rustFile }
    }.mapBreak { return BREAK }

    for (vDir in dirs) {
        val mod = vDir.findChild(RsConstants.MOD_RS_FILE)
        if (mod != null) {
            processor.lazy(vDir.name) {
                psiMgr.findFile(mod)?.rustFile
            }.mapBreak { return BREAK }
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
                processor.lazy(fileName(rawFileName)) {
                    psiMgr.findFile(vFile)?.rustFile
                }.mapBreak { return BREAK }
            }
        }
    }

    return CONTINUE
}

/**
 * Variants from [processExternCrateResolveVariants] + all renamings `extern crate name as alias;`
 * See https://doc.rust-lang.org/reference/names/preludes.html#extern-prelude
 */
fun processExternPreludeResolveVariants(ctx: PathResolutionContext, processor: RsResolveProcessor): ShouldStop {
    val (project, defMap) = ctx.containingModInfo ?: return CONTINUE
    return defMap.externPrelude
        .entriesWithNames(processor.names)
        .asSequence()
        .tryForEach { (name, externCrateDefMap) ->
            val externCrateRoot = externCrateDefMap.rootAsRsMod(project) ?: return@tryForEach CONTINUE
            processor.process(name, externCrateRoot)
        }
}

/** Processes dependencies crates (specified in Cargo.toml) */
fun processExternCrateResolveVariants(
    element: RsElement,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): ShouldStop = processExternCrateResolveVariants(element, isCompletion, !isCompletion, processor)

fun processExternCrateResolveVariants(
    element: RsElement,
    isCompletion: Boolean,
    withSelf: Boolean,
    processor: RsResolveProcessor
): ShouldStop {
    val crate = element.containingCrate

    val visitedDeps = mutableSetOf<String>()
    fun processPackage(crate: Crate, dependencyName: String): ShouldStop {
        val isDependencyOrWorkspace = crate.origin == PackageOrigin.DEPENDENCY || crate.origin == PackageOrigin.WORKSPACE
        if (isCompletion && !isDependencyOrWorkspace) return CONTINUE

        if (crate.origin == PackageOrigin.STDLIB && dependencyName in visitedDeps) return CONTINUE
        visitedDeps.add(dependencyName)

        return processor.lazy(dependencyName) {
            crate.rootModFile?.toPsiFile(element.project)?.rustFile
        }
    }

    if (withSelf) {
        processor.lazy("self") { crate.rootMod }.mapBreak { return BREAK }
    }
    val explicitDepsFirst = crate.dependenciesWithCyclic.sortedBy {
        when (it.crate.origin) {
            PackageOrigin.WORKSPACE,
            PackageOrigin.DEPENDENCY -> 0

            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> 1
        }
    }
    return explicitDepsFirst.tryForEach { dependency ->
        processPackage(dependency.crate, dependency.normName)
    }
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
    var found: RsFile? = null
    val processor = createProcessor(name) {
        if (it.name == name) {
            found = it.element as? RsFile
            BREAK
        } else {
            CONTINUE
        }
    }
    processExternCrateResolveVariants(context, false, processor)
    return found
}

fun processPathResolveVariants(lookup: ImplLookup?, path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): ShouldStop {
    val ctx = PathResolutionContext(path, isCompletion, lookup)
    val pathKind = ctx.classifyPath(path)
    return processPathResolveVariants(ctx, pathKind, processor)
}

fun processPathResolveVariants(ctx: PathResolutionContext, pathKind: RsPathResolveKind, processor: RsResolveProcessor): ShouldStop {
    return when (pathKind) {
        is RsPathResolveKind.UnqualifiedPath -> {
            processSelfSuperCrate(pathKind.ns, ctx, processor)
                .and {
                    processNestedScopesUpwards(
                        ctx.context,
                        pathKind.ns,
                        ctx,
                        processor.withIgnoringSecondaryCSelf()
                    )
                }
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
                processSelfSuperCrate(pathKind.ns, ctx, processor).mapBreak { return BREAK }
            }
            val crateRoot = ctx.crateRoot
            if (crateRoot != null) {
                processItemOrEnumVariantDeclarations(crateRoot, pathKind.ns, processor, withPrivateImports = { true })
            } else {
                CONTINUE
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
): ShouldStop {
    val resolvedQualifier = qualifier.reference?.advancedResolve()
    val primitiveType = TyPrimitive.fromPath(qualifier, checkResolve = false)

    val prevScope = hashMapOf<String, Set<Namespace>>()

    if (resolvedQualifier != null) {
        val shadowingProcessor = if (primitiveType != null) {
            processor.wrapWithBeforeProcessingHandler { e ->
                if (processor.acceptsName(e.name)) {
                    val element = e.element
                    if (element is RsNamedElement) {
                        prevScope[e.name] = element.namespaces
                    }
                }
            }
        } else {
            processor
        }

        processQualifiedPathResolveVariants1(
            ctx,
            isCompletion,
            ns,
            qualifier,
            resolvedQualifier,
            path,
            parent,
            shadowingProcessor
        ).mapBreak { return BREAK }
    }

    // `ctx` is `null` only in the case of macro call path, so  processing associated members makes no sense

    return if (primitiveType != null && ctx != null) {
        processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
            processTypeQualifiedPathResolveVariants(ctx, shadowingProcessor, ns, primitiveType)
        }
    } else CONTINUE
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
): ShouldStop {
    val (base, subst) = resolvedQualifier

    if (parent is RsUseSpeck && path.path == null) {
        SelfInGroup.hit()
        processor.process("self", base).mapBreak { return BREAK }
    }

    if (base is RsMod) {
        processor.lazy("super") {
            // `super` is allowed only after `self` and `super`
            // so we add `super` in completion only when it produces valid path
            if (!isCompletion || isSuperChain(qualifier)) {
                base.`super`
            } else {
                null
            }
        }.mapBreak { return BREAK }

        val containingMod = ctx?.containingMod ?: path.containingMod
        if (Namespace.Macros in ns) {
            processMacros(base, processor, path).mapBreak { return BREAK }
        }

        // Proc macro crates are not allowed to export anything but procedural macros,
        // and all possible macro exports are collected above. However, when resolve
        // happens inside proc macro crate itself, all items are allowed
        val baseModContainingCrate = base.containingCrate
        val resolveBetweenDifferentTargets = baseModContainingCrate != containingMod.containingCrate
        if (resolveBetweenDifferentTargets && baseModContainingCrate.kind.isProcMacro) {
            return CONTINUE
        }
    }

    // `ctx` is `null` only in the case of macro call path, so processing anything but macros makes no sense
    if (ctx == null) return CONTINUE

    val prevScope = hashMapOf<String, Set<Namespace>>()

    // Procedural macros definitions are functions, so they get added twice (once as macros, and once as items). To
    // avoid this, we exclude `MACROS` from passed namespaces
    processWithShadowingAndUpdateScope(prevScope, ns - MACROS, processor) {
        processItemOrEnumVariantDeclarations(
            base,
            ns - MACROS,
            it,
            withPrivateImports = { withPrivateImports(qualifier, base) }
        )
    }.mapBreak { return BREAK }

    return if (base is RsTraitItem && parent !is RsUseSpeck && !qualifier.hasCself) {
        processTraitRelativePath(BoundElement(base, subst), ns, processor)
    } else if (base is RsTypeDeclarationElement && parent !is RsUseSpeck) { // Foo::<Bar>::baz
        val baseTy = if (qualifier.hasCself) {
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
        }

        // Self-qualified type paths `Self::Item` inside impl items are restricted to resolve
        // to only members of the current impl or implemented trait or its parent traits
        val restrictedTraits = if (Namespace.Types in ns && base is RsImplItem && qualifier.hasCself) {
            NameResolutionTestmarks.SelfRelatedTypeSpecialCase.hit()
            base.implementedTrait?.getFlattenHierarchy()
        } else {
            null
        }

        processWithShadowing(prevScope, ns, processor) {
            if (restrictedTraits != null) {
                processTypeAsTraitUFCSQualifiedPathResolveVariants(ns, baseTy, restrictedTraits, it)
            } else {
                processTypeQualifiedPathResolveVariants(ctx, it, ns, baseTy)
            }
        }
    } else CONTINUE
}

/** `<T as Trait>::Item` or `<T>::Item` */
private fun processExplicitTypeQualifiedPathResolveVariants(
    ctx: PathResolutionContext,
    ns: Set<Namespace>,
    typeQual: RsTypeQual,
    processor: RsResolveProcessor
): ShouldStop {
    val trait = typeQual.traitRef?.resolveToBoundTrait()
        // TODO this is a hack to fix completion test `test associated type in explicit UFCS form`.
        // Looks like we should use getOriginalOrSelf during resolve
        ?.let { BoundElement(CompletionUtil.getOriginalOrSelf(it.element), it.subst) }
    val baseTy = typeQual.typeReference.rawType
    return if (trait != null) {
        processTypeAsTraitUFCSQualifiedPathResolveVariants(ns, baseTy, listOf(trait), processor)
    } else {
        processTypeQualifiedPathResolveVariants(ctx, processor, ns, baseTy)
    }
}

private fun processSelfSuperCrate(
    ns: Set<Namespace>,
    ctx: PathResolutionContext,
    processor: RsResolveProcessor
): ShouldStop =
    if (Namespace.Types in ns) {
        processor.lazy("self") { ctx.containingMod }
            .and {
                processor.lazy("super") { ctx.containingMod.`super` }
            }.and {
                ctx.crateRoot
                    ?.let { processor.process("crate", it) }
                    ?: CONTINUE
            }
    } else CONTINUE

private fun processMacroDollarCrateResolveVariants(path: RsPath, processor: RsResolveProcessor): ShouldStop {
    return processor.lazy(MACRO_DOLLAR_CRATE_IDENTIFIER) {
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
): ShouldStop {
    val lookup = ctx.implLookup
    val (normBaseTy, _) = lookup.ctx.normalizeAssociatedTypesIn(baseTy, 0)
    val shadowingProcessor = createProcessorGeneric<AssocItemScopeEntry>(processor.names) { e ->
        if (e.element is RsTypeAlias && baseTy is TyTypeParameter && e.source is TraitImplSource.ExplicitImpl) {
            NameResolutionTestmarks.SkipAssocTypeFromImpl.hit()
            CONTINUE
        } else {
            processor.process(e)
        }
    }
    val selfSubst = if (normBaseTy !is TyTraitObject) {
        mapOf(TyTypeParameter.self() to normBaseTy).toTypeSubst()
    } else {
        emptySubstitution
    }
    return processAssociatedItemsWithSelfSubst(lookup, ctx.context, normBaseTy, ns, selfSubst, shadowingProcessor)
}

/** For `Item` in `<T as Trait>::Item;`, process members of `Trait` */
private fun processTypeAsTraitUFCSQualifiedPathResolveVariants(
    ns: Set<Namespace>,
    baseTy: Ty,
    restrictedTraits: Collection<BoundElement<RsTraitItem>>,
    processor: RsResolveProcessor
): ShouldStop {
    val selfSubst = mapOf(TyTypeParameter.self() to baseTy).toTypeSubst()
    return restrictedTraits.tryForEach { boundTrait ->
        val source = TraitImplSource.Trait(boundTrait.element)
        val subst = boundTrait.subst + selfSubst
        processTraitMembers(boundTrait.element, ns, subst, baseTy, source, processor)
    }
}

/** `TraitName::foo` */
private fun processTraitRelativePath(
    baseBoundTrait: BoundElement<RsTraitItem>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): ShouldStop {
    return baseBoundTrait.getFlattenHierarchy().tryForEach { boundTrait ->
        val source = TraitImplSource.Trait(boundTrait.element)
        processTraitMembers(boundTrait.element, ns, boundTrait.subst, TyUnknown, source, processor)
    }
}

private fun processTraitMembers(
    trait: RsTraitItem,
    ns: Set<Namespace>,
    subst: Substitution,
    selfTy: Ty,
    source: TraitImplSource.Trait,
    processor: RsResolveProcessor
): ShouldStop {
    return trait.members?.expandedMembers.orEmpty().tryForEach { item ->
        val itemNs = when (item) {
            is RsTypeAlias -> Namespace.Types
            else -> Namespace.Values // RsFunction, RsConstant
        }
        if (itemNs !in ns) return@tryForEach CONTINUE
        val name = item.name ?: return@tryForEach CONTINUE
        processor.process(AssocItemScopeEntry(name, item, subst, selfTy, source))
    }
}

fun processPatBindingResolveVariants(
    binding: RsPatBinding,
    isCompletion: Boolean,
    originalProcessor: RsResolveProcessor
): ShouldStop {
    if (binding.parent is RsPatField) {
        val parentPat = binding.parent.parent as RsPatStruct
        val patStruct = parentPat.path.reference?.deepResolve()
        if (patStruct is RsFieldsOwner) {
            processFieldDeclarations(patStruct, originalProcessor).mapBreak { return BREAK }
            if (isCompletion) return CONTINUE
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

fun processLabelResolveVariants(label: RsLabel, processor: RsResolveProcessor): ShouldStop {
    val prevScope = hashMapOf<String, Set<Namespace>>()
    for (scope in label.contexts) {
        if (scope is RsLambdaExpr || scope is RsFunction) return CONTINUE
        if (scope is RsLabeledExpression) {
            val labelDecl = scope.labelDecl ?: continue
            processWithShadowingAndUpdateScope(prevScope, LIFETIMES, processor) {
                it.process(labelDecl)
            }.mapBreak { return BREAK }
        }
    }
    return CONTINUE
}

fun processLifetimeResolveVariants(lifetime: RsLifetime, processor: RsResolveProcessor): ShouldStop {
    if (lifetime.isPredefined) return CONTINUE
    for (scope in lifetime.contexts) {
        val lifetimeParameters = when (scope) {
            is RsGenericDeclaration -> scope.lifetimeParameters
            is RsWhereClause -> scope.wherePredList.mapNotNull { it.forLifetimes }.flatMap { it.lifetimeParameterList }
            is RsForInType -> scope.forLifetimes.lifetimeParameterList
            is RsPolybound -> scope.forLifetimes?.lifetimeParameterList.orEmpty()
            else -> continue
        }
        processAll(lifetimeParameters, processor).mapBreak { return BREAK }
    }

    val owner = lifetime.stubAncestorStrict<RsGenericDeclaration>() ?: return CONTINUE
    if (owner.lifetimeParameters.isEmpty() &&
        IN_BAND_LIFETIMES.availability(lifetime) == FeatureAvailability.AVAILABLE) {
        val lifetimes = mutableListOf<RsLifetime>()
        val lifetimeNames = hashSetOf<String>()
        val visitor = object : RsVisitor() {
            override fun visitLifetime(lifetime: RsLifetime) {
                val name = lifetime.referenceName
                if (name.isNotEmpty() && lifetimeNames.add(name)) {
                    lifetimes.add(lifetime)
                }
            }

            override fun visitElement(element: RsElement) =
                element.stubChildrenOfType<RsElement>().forEach { it.accept(this) }
        }

        (owner as? RsImplItem)?.traitRef?.accept(visitor)
        (owner as? RsImplItem)?.typeReference?.accept(visitor)
        (owner as? RsFunction)?.typeParameterList?.accept(visitor)
        (owner as? RsFunction)?.valueParameterList?.accept(visitor)
        (owner as? RsFunction)?.whereClause?.accept(visitor)

        return processAll(lifetimes, processor)
    }

    return CONTINUE
}

fun processLocalVariables(place: RsElement, originalProcessor: (RsPatBinding) -> Unit) {
    val hygieneFilter = makeHygieneFilter(place)
    walkUp(place, { it is RsItemElement }) { cameFrom, scope ->
        val processor = createProcessor { v ->
            val el = v.element
            if (el is RsPatBinding) originalProcessor(el)
            CONTINUE
        }
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

fun processMacroReferenceVariants(ref: RsMacroReference, processor: RsResolveProcessor): ShouldStop {
    val definition = ref.ancestorStrict<RsMacroCase>() ?: return CONTINUE
    val simple = definition.macroPattern.descendantsOfType<RsMacroBinding>().toList()

    return simple.tryForEach(processor::process)
}

fun processProcMacroResolveVariants(
    path: RsPath,
    originalProcessor: RsResolveProcessor,
    isCompletion: Boolean
): ShouldStop {
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

fun processDeriveTraitResolveVariants(element: RsPath, traitName: String, processor: RsResolveProcessor): ShouldStop {
    processNestedScopesUpwards(element, MACROS, processor)
    // FIXME: support custom items with the same name as known derivable traits (i.e. `foo::bar::Clone`)
    val knownDerive = KNOWN_DERIVABLE_TRAITS[traitName]?.findTrait(element.knownItems)
    return if (knownDerive != null) {
        processor.process(knownDerive)
    } else {
        val traits = RsNamedElementIndex.findElementsByName(element.project, traitName)
            .filterIsInstance<RsTraitItem>()
        processAll(traits, processor)
    }
}

fun processBinaryOpVariants(
    element: RsBinaryOp, operator: OverloadableBinaryOperator,
    processor: RsResolveProcessor
): ShouldStop {
    val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return CONTINUE
    val rhsType = binaryExpr.right?.type ?: return CONTINUE
    val lhsType = binaryExpr.left.type
    val lookup = ImplLookup.relativeTo(element)
    val function = lookup.findOverloadedOpImpl(lhsType, rhsType, operator)
        ?.expandedMembers.orEmpty()
        .functions
        .find { it.name == operator.fnName }
        ?: return CONTINUE
    return processor.process(function)
}

fun processAssocTypeVariants(element: RsAssocTypeBinding, processor: RsResolveProcessor): ShouldStop {
    val trait = element.parentPath?.reference?.resolve() as? RsTraitItem ?: return CONTINUE
    return processAssocTypeVariants(trait, processor)
}

fun processAssocTypeVariants(trait: RsTraitItem, processor: RsResolveProcessor): ShouldStop =
    trait.associatedTypesTransitively.tryForEach(processor::process)

fun processMacroCallPathResolveVariants(path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): ShouldStop {
    val qualifier = path.qualifier
    return if (qualifier == null) {
        if (path.hasColonColon && path.isAtLeastEdition2018) return CONTINUE
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
            resolved?.let { processor.process(it) } ?: CONTINUE
        }
    } else {
        val call = path.parent
        if (call is RsPossibleMacroCall && call.canBeMacroCall) {
            if (isCompletion) {
                /** Note: here we don't have to handle [MACRO_DOLLAR_CRATE_IDENTIFIER] */
                processQualifiedPathResolveVariants(null, isCompletion = true, MACROS, qualifier, path, call, processor)
            } else {
                val def = call.resolveToMacroUsingNewResolve() ?: return CONTINUE
                val name = processor.names?.singleOrNull() ?: def.name ?: return CONTINUE
                processor.process(name, def)
            }
        } else {
            // Allowed only 1 or 2-segment paths: `foo!()` or `foo::bar!()`, but not foo::bar::baz!();
            if (qualifier.path != null) return CONTINUE
            processMacrosExportedByCratePath(path, qualifier, processor)
        }
    }
}

private fun processMacrosExportedByCratePath(context: RsElement, crateName: RsPath, processor: RsResolveProcessor): ShouldStop {
    val crateRoot = findDependencyCrateByNamePath(context, crateName) ?: return CONTINUE
    return processMacrosExportedByCrate(crateRoot, processor)
}

private fun processMacrosExportedByCrate(crateRoot: RsFile, processor: RsResolveProcessor): ShouldStop {
    val exportedMacros = exportedMacrosAsScopeEntries(crateRoot)
    return processAllScopeEntries(exportedMacros, processor)
}

fun processMacroCallVariantsInScope(
    path: RsPath,
    ignoreLegacyMacros: Boolean,
    processor: RsResolveProcessor
): ShouldStop {
    val (result, usedNewResolve) = MacroResolver.processMacrosInLexicalOrderUpward(path, ignoreLegacyMacros, processor)
    if (result) return BREAK
    if (usedNewResolve) return CONTINUE

    val crateRoot = path.crateRoot as? RsFile ?: return CONTINUE
    val prelude = implicitStdlibCrate(crateRoot)?.crateRoot ?: return CONTINUE
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
    private val visitor = MacroResolvingVisitor(reverse = true, ignoreLegacyMacros) { processor.process(it) }

    private fun processMacrosInLexicalOrderUpward(startElement: PsiElement): MacroResolveResult {
        val result = processScopesInLexicalOrderUpward(startElement)
        if (result.result || result.usedNewResolve) return result

        processRemainedExportedMacros().mapBreak { return MacroResolveResult.True }

        val crateRoot = startElement.contextOrSelf<RsElement>()?.crateRoot as? RsFile
            ?: return MacroResolveResult.False
        return processAllScopeEntries(exportedMacrosAsScopeEntries(crateRoot), processor).isBreak.toResult()
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
            visitor.processMacros(e).mapBreak { return MacroResolveResult.True }
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
        if (expandedFrom is RsMacroCall && processExpandedFrom(expandedFrom).isBreak) return MacroResolveResult.True
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
            visitor.processMacros(it.psi).mapBreak { return MacroResolveResult.True }
        }
        // See comment in psiBasedProcessScopesInLexicalOrderUpward
        val expandedFrom = (element.psi as? RsExpandedElement)?.expandedFrom
        if (expandedFrom is RsMacroCall && processExpandedFrom(expandedFrom).isBreak) return MacroResolveResult.True
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
    private fun processExpandedFrom(expandedFrom: RsMacroCall): ShouldStop {
        if (expandedFrom.path.qualifier == null) {
            val resolved = (expandedFrom.path.reference as? RsMacroPathReferenceImpl)
                ?.resolveIfCached()
                ?: return CONTINUE
            return processor.process(expandedFrom.macroName, resolved)
        } else {
            return CONTINUE
        }
    }

    /** Try using new resolve if [element] is top-level item or expanded from top-level macro call. */
    private fun tryProcessAllMacrosUsingNewResolve(element: PsiElement): MacroResolveResult? {
        if (element !is RsElement) return null
        val scope = element.context as? RsItemsOwner ?: return null // we are interested only in top-level elements
        val result = processMacros(scope, processor, path).isBreak
        if (scope !is RsMod && !result) return null  // we should search in parent scopes
        return result.toResult(usedNewResolve = true)
    }

    private fun processRemainedExportedMacros(): ShouldStop {
        return visitor.useItems.tryForEach { useItem ->
            val elements = collectMacrosImportedWithUseItem(useItem, visitor.exportingMacrosCrates)
            processAllScopeEntries(elements, processor)
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
    private val processor: (RsNamedElement) -> ShouldStop
) : RsVisitor() {
    val exportingMacrosCrates = mutableMapOf<String, RsFile>()
    val useItems = mutableListOf<RsUseItem>()
    private var visitorResult: ShouldStop = CONTINUE

    fun processMacros(element: PsiElement): ShouldStop =
        if (element is RsElement) processMacros(element) else CONTINUE

    private fun processMacros(item: RsElement): ShouldStop {
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
                    CONTINUE
                }

                for (item in scope.itemsAndMacros) {
                    visitor.processMacros(item)
                }

                CachedValueProvider.Result.create(visibleMacros, scope.rustStructureOrAnyPsiModificationTracker)
            }

        private fun processAll(
            elements: Collection<RsNamedElement>,
            processor: (RsNamedElement) -> ShouldStop
        ): ShouldStop =
            elements.tryForEach(processor)
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
    return SimpleScopeEntry(macroName, item)
}

private fun List<RsMacro>.toScopeEntries(): List<ScopeEntry> =
    mapNotNull { item -> item.name?.let { SimpleScopeEntry(it, item) } }

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

private fun processFieldDeclarations(struct: RsFieldsOwner, processor: RsResolveProcessor): ShouldStop =
    struct.fields.tryForEach { field ->
        val name = field.name ?: return@tryForEach CONTINUE
        processor.process(name, field)
    }

private fun processMethodDeclarationsWithDeref(
    lookup: ImplLookup,
    receiver: Ty,
    context: RsElement,
    processor: RsMethodResolveProcessor
): ShouldStop {
    return lookup.coercionSequence(receiver).withPrevious().withIndex().tryForEach { (i, tyWithPrev) ->
        val (ty, prevTy) = tyWithPrev
        val tyIsSuitableForMethodCall = ty != TyUnknown && ty !is TyInfer.TyVar
        val withAutoBorrow = (prevTy !is TyReference || prevTy.mutability == MUTABLE) && tyIsSuitableForMethodCall
        val withAutoBorrowMut = (prevTy !is TyReference || prevTy.mutability == IMMUTABLE) && tyIsSuitableForMethodCall
        val methodProcessor = createProcessorGeneric<AssocItemScopeEntry>(processor.names) { (name, element, _, selfTy, source) ->
            val autoBorrow = selfTy is TyReference && selfTy.referenced === ty
            // We intentionally use `hasSelfParameters` instead of `isMethod` because we already know that
            // it is an associated item and so if it is a function with self parameter - it is a method.
            // Also, this place is very hot and `hasSelfParameters` is cheaper than `isMethod`
            if (element is RsFunction
                && element.hasSelfParameters
                && (!autoBorrow || element.selfParameter?.isRef == false)) {
                processor.process(MethodResolveVariant(name, element, selfTy, i, source))
            } else {
                CONTINUE
            }
        }
        processAssociatedItems(lookup, ty, VALUES, context, methodProcessor)
            .and {
                if (withAutoBorrow) {
                    processAssociatedItems(lookup, TyReference(ty, IMMUTABLE), VALUES, context, methodProcessor)
                } else {
                    CONTINUE
                }
            }.and {
                if (withAutoBorrowMut) {
                    processAssociatedItems(lookup, TyReference(ty, MUTABLE), VALUES, context, methodProcessor)
                } else {
                    CONTINUE
                }
            }
    }
}

private fun processAssociatedItems(
    lookup: ImplLookup,
    type: Ty,
    ns: Set<Namespace>,
    context: RsElement,
    processor: RsResolveProcessorBase<AssocItemScopeEntry>
): ShouldStop {
    val nsFilter: (RsAbstractable) -> Boolean = when {
        Namespace.Types in ns && Namespace.Values in ns -> {
            { true }
        }

        Namespace.Types in ns -> {
            { it is RsTypeAlias }
        }

        Namespace.Values in ns -> {
            { it !is RsTypeAlias }
        }

        else -> return CONTINUE
    }

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

            val result = if (traitBounds != null && traitOrImpl is TraitImplSource.TraitBound) {
                // Retrieve trait subst for associated type like
                // trait SliceIndex<T> { type Output; }
                // fn get<I: : SliceIndex<S>>(index: I) -> I::Output
                // Resulting subst will contain mapping T => S
                val bounds = traitBounds.filter { it.element == traitOrImpl.value }
                bounds.tryForEach { processor.process(AssocItemScopeEntry(name, member, it.subst, type, traitOrImpl)) }
            } else {
                processor.process(AssocItemScopeEntry(name, member, emptySubstitution, type, traitOrImpl))
            }
            result.mapBreak { return BREAK }
        }
    }
    return CONTINUE
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
): ShouldStop {
    return processAssociatedItems(lookup, type, ns, context, createProcessorGeneric(processor.names) {
        processor.process(it.copy(subst = it.subst + selfSubst))
    })
}

private fun processLexicalDeclarations(
    scope: RsElement,
    cameFrom: PsiElement,
    ns: Set<Namespace>,
    hygieneFilter: (RsPatBinding) -> Boolean,
    ipm: ItemProcessingMode,
    processor: RsResolveProcessor
): ShouldStop {
    testAssert { cameFrom.context == scope }

    fun processPattern(pattern: RsPat, processor: RsResolveProcessor): ShouldStop {
        if (Namespace.Values !in ns) return CONTINUE
        if (cameFrom == pattern) return CONTINUE

        val alreadyProcessedNames = hashSetOf<String>()

        // Rust allows to defined several bindings in single pattern to the same name,
        // but they all must bind the same variables, hence we can inspect only the first one.
        // See https://github.com/rust-lang/rfcs/blob/master/text/2535-or-patterns.md
        val patternProcessor = processor.wrapWithFilter { e ->
            alreadyProcessedNames.add(e.name) // Process element if it is not in the set
        }

        return PsiTreeUtil.findChildrenOfType(pattern, RsPatBinding::class.java).tryForEach { binding ->
            val name = binding.name ?: return@tryForEach CONTINUE
            patternProcessor.lazy(name) {
                binding.takeIf { (it.parent is RsPatField || !it.isReferenceToConstant) && hygieneFilter(it) }
            }
        }
    }

    fun processLetExprs(
        expr: RsExpr?,
        processor: RsResolveProcessor,
        prevScope: MutableMap<String, Set<Namespace>> = hashMapOf()
    ): ShouldStop {
        if (expr == null || expr == cameFrom) return CONTINUE

        val shadowingProcessor = processor.wrapWithShadowingProcessorAndImmediatelyUpdateScope(prevScope, VALUES)

        fun process(expr: RsExpr): ShouldStop {
            when (expr) {
                cameFrom -> return CONTINUE

                is RsLetExpr -> {
                    val pat = expr.pat ?: return CONTINUE
                    return processPattern(pat, shadowingProcessor)
                }

                is RsBinaryExpr -> {
                    expr.right?.let { process(it) }?.mapBreak { return BREAK }
                    return process(expr.left)
                }

                else -> return CONTINUE
            }
        }


        return process(expr)
    }

    when (scope) {
        is RsMod -> {
            return processItemDeclarations(scope, ns, processor, ipm = ipm)
        }

        is RsTypeAlias -> {
            processAll(scope.typeParameters, processor).mapBreak { return BREAK }
            return if (Namespace.Values in ns) {
                processAll(scope.constParameters, processor)
            } else CONTINUE
        }

        is RsStructItem, is RsEnumItem, is RsTraitOrImpl -> {
            scope as RsGenericDeclaration
            processAll(scope.typeParameters, processor).mapBreak { return BREAK }
            if (Namespace.Values in ns) {
                processAll(scope.constParameters, processor).mapBreak { return BREAK }
            }
            processor.process("Self", scope).mapBreak { return BREAK }
            if (scope is RsImplItem) {
                scope.traitRef?.let { traitRef ->
                    // really should be unnamed, but "_" is not a valid name in rust, so I think it's ok
                    processor.lazy("_") { traitRef.resolveToTrait() }.mapBreak { return BREAK }
                }
            }
            return CONTINUE
        }

        is RsFunction -> {
            if (Namespace.Types in ns) {
                processAll(scope.typeParameters, processor).mapBreak { return BREAK }
            }
            if (Namespace.Values in ns) {
                processAll(scope.constParameters, processor).mapBreak { return BREAK }
            }
            // XXX: `cameFrom is RsBlock` prevents switches to AST in cases like `fn foo(a: usize, b: [u8; SIZE])`.
            // Note that rustc really process them and show [E0435] on this: `fn foo(a: usize, b: [u8; a])`.
            if (Namespace.Values in ns && cameFrom is RsBlock) {
                val selfParam = scope.selfParameter
                if (selfParam != null) {
                    processor.process("self", selfParam).mapBreak { return BREAK }
                }

                for (parameter in scope.valueParameters) {
                    val pat = parameter.pat ?: continue
                    processPattern(pat, processor).mapBreak { return BREAK }
                }
            }
            return CONTINUE
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
                    processPattern(pat, shadowingProcessor).mapBreak { return BREAK }
                }
            }

            return processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                processItemDeclarations(scope as RsItemsOwner, ns, shadowingProcessor, ItemProcessingMode.WITH_PRIVATE_IMPORTS)
            }
        }

        is RsForExpr -> {
            if (scope.expr == cameFrom) return CONTINUE
            if (Namespace.Values !in ns) return CONTINUE
            val pat = scope.pat
            return if (pat != null) {
                processPattern(pat, processor)
            } else CONTINUE
        }

        is RsIfExpr -> {
            return if (scope.block == cameFrom) {
                val expr = scope.condition?.expr
                processLetExprs(expr, processor)
            } else CONTINUE
        }

        is RsWhileExpr -> {
            return if (scope.block == cameFrom) {
                val expr = scope.condition?.expr
                processLetExprs(expr, processor)
            } else CONTINUE
        }

        is RsBinaryExpr -> {
            return if (scope.right == cameFrom) {
                processLetExprs(scope.left, processor)
            } else CONTINUE
        }

        is RsLambdaExpr -> {
            if (Namespace.Values !in ns) return CONTINUE
            return scope.valueParameters.tryForEach { parameter ->
                val pat = parameter.pat
                if (pat != null) {
                    processPattern(pat, processor)
                } else CONTINUE
            }
        }

        is RsMatchArm -> {
            val guardExpr = scope.matchArmGuard?.expr
            if (guardExpr == null || scope.expr != cameFrom) return processPattern(scope.pat, processor)
            val prevScope = hashMapOf<String, Set<Namespace>>()
            return processLetExprs(guardExpr, processor, prevScope).and {
                processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                    processPattern(scope.pat, shadowingProcessor)
                }
            }
        }

        else -> return CONTINUE
    }
}

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): ShouldStop = processNestedScopesUpwards(scopeStart, ns, null, processor)

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    ns: Set<Namespace>,
    ctx: PathResolutionContext?,
    processor: RsResolveProcessor
): ShouldStop {
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
            } ?: return@walkUp CONTINUE

            processWithShadowingAndUpdateScope(prevScope, ns, processor) { shadowingProcessor ->
                val ipm = ItemProcessingMode.WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES
                processItemDeclarationsUsingModInfo(scopeIsMod = true, modInfo, ns, shadowingProcessor, ipm)
            }.mapBreak {
                return@walkUp BREAK
            }

            val preludeModInfo = findPreludeUsingModInfo(modInfo)
            if (preludeModInfo != null) {
                return@walkUp processWithShadowing(prevScope, ns, processor) { shadowingProcessor ->
                    processItemDeclarationsUsingModInfo(scopeIsMod = true, preludeModInfo, ns, shadowingProcessor, ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS)
                }
            }

            CONTINUE
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
    f: (RsResolveProcessor) -> ShouldStop
): ShouldStop {
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
    f: (RsResolveProcessor) -> ShouldStop
): ShouldStop {
    val shadowingProcessor = processor.wrapWithShadowingProcessor(prevScope, ns)
    return f(shadowingProcessor)
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
    return crateRoot?.let { ImplicitStdlibCrate(name, it) }
}

// There's already similar functions in TreeUtils, should use it
private fun walkUp(
    start: PsiElement,
    stopAfter: (RsElement) -> Boolean,
    stopAtFileBoundary: Boolean = true,
    processor: (cameFrom: PsiElement, scope: RsElement) -> ShouldStop
): ShouldStop {
    var cameFrom = start
    var scope = start.context as RsElement?
    while (scope != null) {
        ProgressManager.checkCanceled()
        processor(cameFrom, scope).mapBreak { return BREAK }
        if (stopAfter(scope)) break
        cameFrom = scope
        scope = scope.context as? RsElement
        if (!stopAtFileBoundary) {
            if (scope == null && cameFrom is RsFile) {
                scope = cameFrom.`super`
            }
        }
    }
    return CONTINUE
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

