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
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.hitOnFalse
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.RsConstants
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.IN_BAND_LIFETIMES
import org.rust.lang.core.completion.RsMacroCompletionProvider
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile.Attributes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.NameResolutionTestmarks.crateRootModule
import org.rust.lang.core.resolve.NameResolutionTestmarks.missingMacroUse
import org.rust.lang.core.resolve.NameResolutionTestmarks.modDeclExplicitPathInInlineModule
import org.rust.lang.core.resolve.NameResolutionTestmarks.modDeclExplicitPathInNonInlineModule
import org.rust.lang.core.resolve.NameResolutionTestmarks.modRsFile
import org.rust.lang.core.resolve.NameResolutionTestmarks.selfInGroup
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.indexes.RsMacroIndex
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.resolve2.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.infer.foldCtConstParameterWith
import org.rust.lang.core.types.infer.foldTyTypeParameterWith
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.testAssert
import org.rust.openapiext.toPsiFile
import org.rust.stdext.buildList
import org.rust.stdext.intersects

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

private val LOG = Logger.getInstance("org.rust.lang.core.resolve.NameResolution")

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
    for ((i, ty) in lookup.coercionSequence(receiverType).withIndex()) {
        if (ty !is TyAdt || ty.item !is RsStructItem) continue
        val processor = createProcessor(originalProcessor.name) {
            originalProcessor(FieldResolveVariant(it.name, it.element!!, ty, i))
        }
        if (processFieldDeclarations(ty.item, processor)) return true
    }

    return false
}

fun processStructLiteralFieldResolveVariants(
    field: RsStructLiteralField,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val resolved = field.parentStructLiteral.path.reference?.deepResolve()
    val structOrEnumVariant = resolved as? RsFieldsOwner ?: return false
    if (processFieldDeclarations(structOrEnumVariant, processor)) return true
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
            modDeclExplicitPathInNonInlineModule.hit()
            contextualFile.parent
        } else {
            modDeclExplicitPathInInlineModule.hit()
            ownedDirectory
        } ?: return false
        val vFile = dir.virtualFile.findFileByRelativePath(FileUtil.toSystemIndependentName(explicitPath))
            ?: return false
        val mod = psiMgr.findFile(vFile)?.rustFile ?: return false

        val name = modDecl.name ?: return false
        return processor(name, mod)
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
        if (processor.lazy(fileName(rawFileName)) { psiMgr.findFile(vFile)?.rustFile }) return true
    }

    for (vDir in dirs) {
        val mod = vDir.findChild(RsConstants.MOD_RS_FILE)
        if (mod != null) {
            if (processor.lazy(vDir.name) { psiMgr.findFile(mod)?.rustFile }) return true
        }

        // We shouldn't search possible module files in subdirectories
        // if module declaration is located in `mod.rs`
        if (inModRs) {
            modRsFile.hit()
            continue
        }

        // Submodule file of crate root (for example, `mod foo;` in `src/main.rs`)
        // can be located in the same directory with parent module (i.e. in `src/foo.rs`)
        // or in `mod.rs` of subdirectory of crate root dir (i.e. in `src/foo/mod.rs`)
        // Both cases are handled above
        if (inCrateRoot.value) {
            crateRootModule.hit()
            continue
        }

        if (vDir.name == containingMod.modName) {
            for (vFile in vDir.children) {
                if (vFile.isDirectory) continue
                val rawFileName = vFile.name
                if (rawFileName == RsConstants.MOD_RS_FILE) continue
                if (processor.lazy(fileName(rawFileName)) { psiMgr.findFile(vFile)?.rustFile }) return true
            }
        }
    }

    return false
}

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
    val crate = element.containingCrate ?: return false

    val visitedDeps = mutableSetOf<String>()
    fun processPackage(crate: Crate, dependencyName: String): Boolean {
        if (isCompletion && crate.origin != PackageOrigin.DEPENDENCY) return false

        if (crate.origin == PackageOrigin.STDLIB && dependencyName in visitedDeps) return false
        visitedDeps.add(dependencyName)

        return processor.lazy(dependencyName) {
            crate.rootModFile?.toPsiFile(element.project)?.rustFile
        }
    }

    if (withSelf) {
        if (processor.lazy("self") { crate.rootMod }) return true
    }
    val explicitDepsFirst = crate.dependenciesWithCyclic.sortedBy {
        when (it.crate.origin) {
            PackageOrigin.WORKSPACE,
            PackageOrigin.DEPENDENCY -> {
                NameResolutionTestmarks.shadowingStdCrates.hit()
                0
            }
            PackageOrigin.STDLIB -> 1
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
    var found: RsFile? = null
    val processor = createProcessor(name) {
        if (it.name == name) {
            found = it.element as? RsFile
            true
        } else {
            false
        }
    }
    processExternCrateResolveVariants(context, false, processor)
    return found
}

fun processPathResolveVariants(lookup: ImplLookup?, path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    val parent = path.context
    if (parent is RsMacroCall) {
        error("Tried to use `processPathResolveVariants` for macro path. See `RsMacroPathReferenceImpl`")
    }
    val qualifier = path.qualifier
    val typeQual = path.typeQual
    val ns = path.allowedNamespaces(isCompletion)

    // RsPathExpr can became a macro by adding a trailing `!`, so we add macros to completion
    if (isCompletion && parent is RsPathExpr && qualifier?.path == null) {
        if (processMacroCallPathResolveVariants(path, true, processor)) return true
    }

    return when {
        // foo::bar
        qualifier != null ->
            processQualifiedPathResolveVariants(lookup, isCompletion, ns, qualifier, path, parent, processor)

        // `<T as Trait>::Item` or `<T>::Item`
        typeQual != null ->
            processExplicitTypeQualifiedPathResolveVariants(lookup, path, ns, typeQual, processor)

        else -> processUnqualifiedPathResolveVariants(isCompletion, ns, path, parent, processor)
    }
}

/**
 * foo::bar
 * |    |
 * |    [path]
 * [qualifier]
 */
private fun processQualifiedPathResolveVariants(
    lookup: ImplLookup?,
    isCompletion: Boolean,
    ns: Set<Namespace>,
    qualifier: RsPath,
    path: RsPath,
    parent: PsiElement?,
    processor: RsResolveProcessor
): Boolean {
    val (base, subst) = qualifier.reference?.advancedResolve() ?: run {
        val primitiveType = TyPrimitive.fromPath(qualifier)
        if (primitiveType != null) {
            if (processTypeQualifiedPathResolveVariants(lookup, path, processor, ns, primitiveType, null)) return true
        }
        return false
    }
    if (base is RsMod) {
        val result = processor.lazy("super") {
            // `super` is allowed only after `self` and `super`
            // so we add `super` in completion only when it produces valid path
            if (!isCompletion || isSuperChain(qualifier)) {
                base.`super`
            } else {
                null
            }
        }
        if (result) return true

        val containingMod = path.containingMod
        if (Namespace.Macros in ns) {
            val resultWithNewResolve = processMacros(base, processor, null)
            if (resultWithNewResolve == true) return true
            if (resultWithNewResolve == null && base is RsFile && base.isCrateRoot &&
                containingMod is RsFile && containingMod.isCrateRoot) {
                if (processAllScopeEntries(exportedMacrosAsScopeEntries(base), processor)) return true
            }
        }

        // Proc macro crates are not allowed to export anything but procedural macros,
        // and all possible macro exports are collected above. However, when resolve
        // happens inside proc macro crate itself, all items are allowed
        val baseModContainingCrate = base.containingCrate
        val resolveBetweenDifferentTargets = baseModContainingCrate != containingMod.containingCrate
        if (resolveBetweenDifferentTargets && baseModContainingCrate?.kind?.isProcMacro == true) {
            return false
        }
    }

    if (parent is RsUseSpeck && path.path == null) {
        selfInGroup.hit()
        if (processor("self", base)) return true
    }

    // Procedural macros definitions are functions, so they get added twice (once as macros, and once as items). To
    // avoid this, we exclude `MACROS` from passed namespaces
    if (processItemOrEnumVariantDeclarations(
            base,
            ns - MACROS,
            processor,
            withPrivateImports = { withPrivateImports(qualifier, base) }
        )) {
        return true
    }

    if (base is RsTraitItem && parent !is RsUseSpeck && !qualifier.hasCself) {
        if (processTraitRelativePath(BoundElement(base, subst), ns, processor)) return true
    } else if (base is RsTypeDeclarationElement && parent !is RsUseSpeck) { // Foo::<Bar>::baz
        val baseTy = if (qualifier.hasCself) {
            when (base) {
                // impl S { fn foo() { Self::bar() } }
                is RsImplItem -> base.typeReference?.type ?: TyUnknown
                is RsTraitItem -> TyTypeParameter.self(base)
                else -> TyUnknown
            }
        } else {
            val realSubst = if (qualifier.typeArgumentList != null) {
                // If the path contains explicit type arguments `Foo::<_, Bar, _>::baz`
                // it means that all possible `TyInfer` has already substituted (with `_`)
                subst
            } else {
                subst
                    .mapTypeValues { (_, v) ->
                        v.foldTyTypeParameterWith { TyInfer.TyVar(it) }
                            .foldCtConstParameterWith { CtInferVar(it) }
                    }
                    .mapConstValues { (_, v) -> v.foldCtConstParameterWith { CtInferVar(it) } }
            }
            base.declaredType.substitute(realSubst)
        }

        // Self-qualified type paths `Self::Item` inside impl items are restricted to resolve
        // to only members of the current impl or implemented trait or its parent traits
        val restrictedTraits = if (Namespace.Types in ns && base is RsImplItem && qualifier.hasCself) {
            NameResolutionTestmarks.selfRelatedTypeSpecialCase.hit()
            base.implementedTrait?.flattenHierarchy
                ?.map { value ->
                    value
                        .foldTyTypeParameterWith { TyInfer.TyVar(it) }
                        .foldCtConstParameterWith { CtInferVar(it) }
                }
        } else {
            null
        }

        if (processTypeQualifiedPathResolveVariants(lookup, path, processor, ns, baseTy, restrictedTraits)) return true
    }
    return false
}

/** `<T as Trait>::Item` or `<T>::Item` */
private fun processExplicitTypeQualifiedPathResolveVariants(
    lookup: ImplLookup?,
    path: RsPath,
    ns: Set<Namespace>,
    typeQual: RsTypeQual,
    processor: RsResolveProcessor
): Boolean {
    val trait = typeQual.traitRef?.resolveToBoundTrait()
        // TODO this is a hack to fix completion test `test associated type in explicit UFCS form`.
        // Looks like we should use getOriginalOrSelf during resolve
        ?.let { BoundElement(CompletionUtil.getOriginalOrSelf(it.element), it.subst) }
        ?.let { value ->
            value
                .foldTyTypeParameterWith { TyInfer.TyVar(it) }
                .foldCtConstParameterWith { CtInferVar(it) }
        }
    val type = typeQual.typeReference.type
    return processTypeQualifiedPathResolveVariants(lookup, path, processor, ns, type, trait?.let { listOf(it) })
}

private fun processUnqualifiedPathResolveVariants(
    isCompletion: Boolean,
    ns: Set<Namespace>,
    path: RsPath,
    parent: PsiElement?,
    processor: RsResolveProcessor
): Boolean {
    if (isCompletion) {
        // Complete possible associated types in a case like `Trait</*caret*/>`
        val possibleTypeArgs = parent?.parent
        if (possibleTypeArgs is RsTypeArgumentList) {
            val trait = (possibleTypeArgs.parent as? RsPath)?.reference?.resolve() as? RsTraitItem
            if (trait != null && processAssocTypeVariants(trait, processor)) return true
        }
    }

    val containingMod = path.containingMod
    val crateRoot = containingMod.crateRoot

    /** Path starts with `::` */
    val hasColonColon = path.hasColonColon
    if (!hasColonColon) {
        run {
            // hacks around $crate macro metavar. See `expandDollarCrateVar` function docs
            val referenceName = path.referenceName
            if (referenceName == MACRO_DOLLAR_CRATE_IDENTIFIER) {
                val result = processor.lazy(referenceName) {
                    path.resolveDollarCrateIdentifier()?.rootMod
                }
                if (result) return true
            }
        }
        if (Namespace.Types in ns) {
            if (processor("self", containingMod)) return true
            if (processor.lazy("super") { containingMod.`super` }) return true
            if (crateRoot != null && processor("crate", crateRoot)) return true
        }
    }

    val isEdition2018 = (crateRoot ?: containingMod).isEdition2018

    // In 2015 edition a path is crate-relative (global) if it's inside use item,
    // inside "visibility restriction" or if it starts with `::`
    // ```rust, edition2015
    // use foo::bar; // `foo` is crate-relative
    // let a = ::foo::bar; // `foo` is also crate-relative
    // pub(in foo::bar) fn baz() {}
    //       //^ crate-relative path too
    // ```
    // In 2018 edition a path is crate-relative if it starts with `crate::` (handled above)
    // or if it's inside "visibility restriction". `::`-qualified path on 2018 edition means that
    // such path is a name of some dependency crate (that should be resolved without `extern crate`)
    val isCrateRelative = !isEdition2018 && (hasColonColon || path.rootPath().parent is RsUseSpeck)
        || path.rootPath().parent is RsVisRestriction
    // see https://doc.rust-lang.org/edition-guide/rust-2018/module-system/path-clarity.html#the-crate-keyword-refers-to-the-current-crate
    val isExternCrate = isEdition2018 && hasColonColon
    return when {
        isCrateRelative -> if (crateRoot != null) {
            processItemOrEnumVariantDeclarations(crateRoot, ns, processor, withPrivateImports = { true })
        } else {
            false
        }

        isExternCrate -> processExternCrateResolveVariants(path, isCompletion, processor)

        else -> processNestedScopesUpwards(path, ns, isCompletion, processor)
    }
}

fun RsPath.resolveDollarCrateIdentifier(): Crate? {
    NameResolutionTestmarks.dollarCrateMagicIdentifier.hit()
    val dollarCrateSource = findMacroCallFromWhichLeafIsExpanded() ?: this
    return dollarCrateSource
        .findMacroCallExpandedFromNonRecursive()
        ?.resolveToMacroAndGetContainingCrate()
}

private fun processTypeQualifiedPathResolveVariants(
    lookup: ImplLookup?,
    path: RsPath,
    processor: RsResolveProcessor,
    ns: Set<Namespace>,
    baseTy: Ty,
    restrictedTraits: List<BoundElement<RsTraitItem>>?
): Boolean {
    @Suppress("NAME_SHADOWING")
    val lookup = lookup ?: ImplLookup.relativeTo(path)
    val shadowingProcessor = if (restrictedTraits != null) {
        fun(e: AssocItemScopeEntry): Boolean {
            if (e.element !is RsTypeAlias) return processor(e)

            val implementedTrait = e.source.implementedTrait
                ?.foldTyTypeParameterWith { TyInfer.TyVar(it) }
                ?.foldCtConstParameterWith { CtInferVar(it) }
                ?: return processor(e)

            val isAppropriateTrait = restrictedTraits.any {
                lookup.ctx.probe { lookup.ctx.combineBoundElements(it, implementedTrait) }
            }
            return if (isAppropriateTrait) processor(e) else false
        }
    } else {
        fun(e: AssocItemScopeEntry): Boolean {
            return if (e.element is RsTypeAlias && baseTy is TyTypeParameter &&
                e.source is TraitImplSource.ExplicitImpl) {
                NameResolutionTestmarks.skipAssocTypeFromImpl.hit()
                false
            } else {
                processor(e)
            }
        }
    }
    val selfSubst = if (baseTy !is TyTraitObject) {
        mapOf(TyTypeParameter.self() to baseTy).toTypeSubst()
    } else {
        emptySubstitution
    }
    if (processAssociatedItemsWithSelfSubst(lookup, path, baseTy, ns, selfSubst, shadowingProcessor)) return true
    return false
}

/** `TraitName::foo` */
private fun processTraitRelativePath(
    baseBoundTrait: BoundElement<RsTraitItem>,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean {
    for (boundTrait in baseBoundTrait.flattenHierarchy) {
        val trait = boundTrait.element
        val source = TraitImplSource.Trait(trait)
        for (item in trait.members?.expandedMembers.orEmpty()) {
            val itemNs = when (item) {
                is RsTypeAlias -> Namespace.Types
                else -> Namespace.Values // RsFunction, RsConstant
            }
            if (itemNs !in ns) continue
            val name = item.name ?: continue
            if (processor(AssocItemScopeEntry(name, item, boundTrait.subst, TyUnknown, source))) return true
        }
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

    val processor = createProcessor(originalProcessor.name) { entry ->
        originalProcessor.lazy(entry.name) {
            val element = entry.element ?: return@lazy null
            val isConstant = element.isConstantLike
            val isPathOrDestructable = when (element) {
                is RsMod, is RsEnumItem, is RsEnumVariant, is RsStructItem -> true
                else -> false
            }
            if (isConstant || (isCompletion && isPathOrDestructable)) element else null
        }
    }
    return processNestedScopesUpwards(binding, if (isCompletion) TYPES_N_VALUES else VALUES, processor)
}

fun processLabelResolveVariants(label: RsLabel, processor: RsResolveProcessor): Boolean {
    for (scope in label.ancestors) {
        if (scope is RsLambdaExpr || scope is RsFunction) return false
        if (scope is RsLabeledExpression) {
            val labelDecl = scope.labelDecl ?: continue
            if (processor(labelDecl)) return true
        }
    }
    return false
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
        if (processAll(lifetimeParameters, processor)) return true
    }

    val owner = lifetime.stubAncestorStrict<RsGenericDeclaration>() ?: return false
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
        owner.typeParameterList?.accept(visitor)
        (owner as? RsFunction)?.valueParameterList?.accept(visitor)
        owner.whereClause?.accept(visitor)
        return processAll(lifetimes, processor)
    }

    return false
}

fun processLocalVariables(place: RsElement, originalProcessor: (RsPatBinding) -> Unit) {
    val hygieneFilter = makeHygieneFilter(place)
    walkUp(place, { it is RsItemElement }) { cameFrom, scope ->
        val processor = createProcessor { v ->
            val el = v.element
            if (el is RsPatBinding) originalProcessor(el)
            false
        }
        processLexicalDeclarations(scope, cameFrom, VALUES, hygieneFilter, ItemProcessingMode.WITH_PRIVATE_IMPORTS, processor)
    }
}

/**
 * Resolves an absolute path.
 */
fun resolveStringPath(path: String, workspace: CargoWorkspace, project: Project): Pair<RsNamedElement, CargoWorkspace.Package>? {
    check(!path.startsWith("::"))
    val parts = path.split("::", limit = 2)
    if (parts.size != 2) return null
    val pkg = workspace.findPackage(parts[0]) ?: run {
        return if (isUnitTestMode) {
            // Allows to set a fake path for some item in tests via
            // lang attribute, e.g. `#[lang = "std::iter::Iterator"]`
            RsLangItemIndex.findLangItem(project, path)?.let { it to workspace.packages.first() }
        } else {
            null
        }
    }

    val el = pkg.targets.asSequence()
        .mapNotNull { RsCodeFragmentFactory(project).createCrateRelativePath(parts[1], it) }
        .mapNotNull { it.reference?.resolve() }
        .filterIsInstance<RsNamedElement>()
        .firstOrNull() ?: return null
    return el to pkg
}

fun processMacroReferenceVariants(ref: RsMacroReference, processor: RsResolveProcessor): Boolean {
    val definition = ref.ancestorStrict<RsMacroCase>() ?: return false
    val simple = definition.macroPattern.descendantsOfType<RsMacroBinding>()
        .toList()

    return simple.any { processor(it) }
}

/**
 * Attribute macro can only be resolved to its definition.
 */
fun processAttributeProcMacroResolveVariants(element: RsPath, processor: RsResolveProcessor): Boolean =
    processNestedScopesUpwards(element, MACROS, processor)

fun processDeriveTraitResolveVariants(element: RsPath, traitName: String, processor: RsResolveProcessor): Boolean {
    processNestedScopesUpwards(element, MACROS, processor)
    // FIXME: support custom items with the same name as known derivable traits (i.e. `foo::bar::Clone`)
    val knownDerive = KNOWN_DERIVABLE_TRAITS[traitName]?.findTrait(element.knownItems)
    return if (knownDerive != null) {
        processor(knownDerive)
    } else {
        val traits = RsNamedElementIndex.findElementsByName(element.project, traitName)
            .filterIsInstance<RsTraitItem>()
        processAll(traits, processor)
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
    return processor(function)
}

fun processAssocTypeVariants(element: RsAssocTypeBinding, processor: RsResolveProcessor): Boolean {
    val trait = element.parentPath?.reference?.resolve() as? RsTraitItem ?: return false
    return processAssocTypeVariants(trait, processor)
}

fun processAssocTypeVariants(trait: RsTraitItem, processor: RsResolveProcessor): Boolean {
    if (trait.associatedTypesTransitively.any { processor(it) }) return true
    return false
}

fun processMacroCallPathResolveVariants(path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    // Allowed only 1 or 2-segment paths: `foo!()` or `foo::bar!()`, but not foo::bar::baz!();
    val qualifier = path.qualifier
    if (qualifier?.path != null) return false
    return if (qualifier == null) {
        if (isCompletion) {
            processMacroCallVariantsInScope(path, processor)
        } else {
            if (!path.cameFromMacroCall()) {
                // Handles `#[macro_export(local_inner_macros)]`
                // this "recursive" macro resolve should not be a problem because
                // 1. we resolve the macro from which [path] is expanded, so it can't run into infinite recursion
                // 2. we expand macros step-by-step, so the result of such resolution should be cached already
                val expandedFrom = path.findMacroCallExpandedFromNonRecursive()
                expandedFrom
                    ?.resolveToMacroAndProcessLocalInnerMacros(processor) { def ->
                        /* this code will be executed if new resolve can't be used */
                        if (!def.hasMacroExportLocalInnerMacros) return@resolveToMacroAndProcessLocalInnerMacros null
                        val crateRoot = def.crateRoot as? RsFile ?: return@resolveToMacroAndProcessLocalInnerMacros false
                        processAll(exportedMacros(crateRoot), processor)
                    }
                    ?.let { return it }
            }

            val resolved = pickFirstResolveEntry(path.referenceName) { processMacroCallVariantsInScope(path, it) }
            resolved?.let { processor(it) } ?: false
        }
    } else {
        val call = path.parent
        if (path.project.isNewResolveEnabled && call is RsMacroCall && call.isTopLevelExpansion) {
            if (isCompletion) {
                /** Note: here we don't have to handle [MACRO_DOLLAR_CRATE_IDENTIFIER] */
                processQualifiedPathResolveVariants(null, isCompletion, MACROS, qualifier, path, call, processor)
            } else {
                val def = call.resolveToMacroUsingNewResolve() ?: return false
                val name = processor.name ?: def.name ?: return false
                processor(name, def)
            }
        } else {
            processMacrosExportedByCratePath(path, qualifier, processor)
        }
    }
}

private fun processMacrosExportedByCratePath(context: RsElement, crateName: RsPath, processor: RsResolveProcessor): Boolean {
    val crateRoot = findDependencyCrateByNamePath(context, crateName) ?: return false
    return processMacrosExportedByCrate(crateRoot, processor)
}

fun processMacrosExportedByCrateName(context: RsElement, crateName: String, processor: RsResolveProcessor): Boolean {
    val crateRoot = findDependencyCrateByName(context, crateName) ?: return false
    return processMacrosExportedByCrate(crateRoot, processor)
}

private fun processMacrosExportedByCrate(crateRoot: RsFile, processor: RsResolveProcessor): Boolean {
    val exportedMacros = exportedMacrosAsScopeEntries(crateRoot)
    return processAllScopeEntries(exportedMacros, processor)
}

fun processMacroCallVariantsInScope(context: PsiElement, processor: RsResolveProcessor): Boolean {
    val (result, usedNewResolve) = MacroResolver.processMacrosInLexicalOrderUpward(context, processor)
    if (result || usedNewResolve) return result

    val element = context.contextOrSelf<RsElement>() ?: return false
    val crateRoot = element.crateRoot as? RsFile ?: return false
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
    /** `RsPath` in resolve, `PsiElement(identifier)` in completion by [RsMacroCompletionProvider] */
    private val macroPath: PsiElement,
) : RsVisitor() {
    private val visitor = MacroResolvingVisitor(reverse = true) { processor(it) }
    private val project: Project = macroPath.project

    private fun processMacrosInLexicalOrderUpward(startElement: PsiElement): MacroResolveResult {
        val result = processScopesInLexicalOrderUpward(startElement)
        if (result.result || result.usedNewResolve) return result

        if (processRemainedExportedMacros()) return MacroResolveResult.True

        val crateRoot = startElement.contextOrSelf<RsElement>()?.crateRoot as? RsFile
            ?: return MacroResolveResult.False
        NameResolutionTestmarks.processSelfCrateExportedMacros.hit()
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
        if (expandedFrom != null && processExpandedFrom(expandedFrom)) return MacroResolveResult.True
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
        if (expandedFrom != null && processExpandedFrom(expandedFrom)) return MacroResolveResult.True
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
            if (processor(expandedFrom.macroName, resolved)) return true
        }
        return false
    }

    /** Try using new resolve if [element] is top-level item or expanded from top-level macro call. */
    private fun tryProcessAllMacrosUsingNewResolve(element: PsiElement): MacroResolveResult? {
        if (!project.isNewResolveEnabled) return null
        if (element !is RsElement) return null
        if (element.parent !is RsMod) return null  // we are interested only in top-level elements

        val expandedFrom = (element as? RsExpandedElement)?.expandedFromRecursively
        val item = expandedFrom ?: element
        val scope = item.parent as? RsMod ?: return null
        /** [processRemainedExportedMacros] processes local imports */
        return processMacros(scope, processor, macroPath, ::processRemainedExportedMacros)
            ?.toResult(usedNewResolve = true)
    }

    private fun processRemainedExportedMacros(): Boolean {
        return visitor.useItems.any { useItem ->
            processAllScopeEntries(collectMacrosImportedWithUseItem(useItem, visitor.exportingMacrosCrates), processor)
        }
    }

    companion object {
        fun processMacrosInLexicalOrderUpward(startElement: PsiElement, processor: RsResolveProcessor): MacroResolveResult =
            MacroResolver(processor, startElement).processMacrosInLexicalOrderUpward(startElement)

        private fun Boolean.toResult(usedNewResolve: Boolean = false): MacroResolveResult =
            MacroResolveResult(this, usedNewResolve)
    }
}

private class MacroResolvingVisitor(
    val reverse: Boolean,
    val processor: (RsNamedElement) -> Boolean
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
        visitorResult = processor(item)
    }

    override fun visitModItem(item: RsModItem) {
        if (missingMacroUse.hitOnFalse(item.hasMacroUse) && item.isEnabledByCfgSelf) {
            val elements = visibleMacros(item)
            visitorResult = processAll(if (reverse) elements.asReversed() else elements, processor)
        }
    }

    override fun visitModDeclItem(item: RsModDeclItem) {
        if (missingMacroUse.hitOnFalse(item.hasMacroUse) && item.isEnabledByCfgSelf) {
            val mod = item.reference.resolve() as? RsMod ?: return
            val elements = visibleMacros(mod)
            visitorResult = processAll(if (reverse) elements.asReversed() else elements, processor)
        }
    }

    override fun visitExternCrateItem(item: RsExternCrateItem) {
        if (!item.isEnabledByCfgSelf) return
        val mod = item.reference.resolve() as? RsFile ?: return
        if (missingMacroUse.hitOnFalse(item.hasMacroUse)) {
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
                val visitor = MacroResolvingVisitor(reverse = false) {
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
    val cacheKey = if (scope.project.macroExpansionManager.expansionState != null) EXPORTED_MACROS_KEY else EXPORTED_KEY
    return CachedValuesManager.getCachedValue(scope, cacheKey) {
        val macros = exportedMacrosInternal(scope)
        CachedValueProvider.Result.create(macros, scope.rustStructureOrAnyPsiModificationTracker)
    }
}

private val EXPORTED_KEY: Key<CachedValue<List<ScopeEntry>>> = Key.create("EXPORTED_KEY")
private val EXPORTED_MACROS_KEY: Key<CachedValue<List<ScopeEntry>>> = Key.create("EXPORTED_MACROS_KEY")

private fun exportedMacrosInternal(scope: RsFile): List<ScopeEntry> {
    // proc-macro crates are allowed to export only procedural macros.
    if (scope.containingCrate?.kind?.isProcMacro == true) {
        return scope.stubChildrenOfType<RsFunction>().mapNotNull { asProcMacroDefinition(it) }
    }

    val allExportedMacros = RsMacroIndex.allExportedMacros(scope.project)
    return buildList {
        addAll(allExportedMacros[scope]?.toScopeEntries().orEmpty())

        val exportingMacrosCrates = mutableMapOf<String, RsMod>()

        val externCrates = scope.stubChildrenOfType<RsExternCrateItem>()
        for (item in externCrates) {
            if (!item.isEnabledByCfgSelf) continue
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
    if (twoSegmentPaths.isEmpty() || !useItem.isEnabledByCfgSelf) return emptyList()

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
        processor(name, field)
    }

private fun processMethodDeclarationsWithDeref(
    lookup: ImplLookup,
    receiver: Ty,
    context: RsElement,
    processor: RsMethodResolveProcessor
): Boolean {
    return lookup.coercionSequence(receiver).withIndex().any { (i, ty) ->
        val methodProcessor: (AssocItemScopeEntry) -> Boolean = { (name, element, _, _, source) ->
            // We intentionally use `hasSelfParameters` instead of `isMethod` because we already know that
            // it is an associated item and so if it is a function with self parameter - it is a method.
            // Also, this place is very hot and `hasSelfParameters` is cheaper than `isMethod`
            element is RsFunction && element.hasSelfParameters &&
                processor(MethodResolveVariant(name, element, ty, i, source))
        }
        processAssociatedItems(lookup, ty, VALUES, context, methodProcessor)
    }
}

private fun processAssociatedItems(
    lookup: ImplLookup,
    type: Ty,
    ns: Set<Namespace>,
    context: RsElement,
    processor: (AssocItemScopeEntry) -> Boolean
): Boolean {
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
        else -> return false
    }

    val traitBounds = (type as? TyTypeParameter)?.let { lookup.getEnvBoundTransitivelyFor(it).toList() }
    val visitedInherent = hashMapOf<String, RsAbstractable>()
    val contextMod = lazy(LazyThreadSafetyMode.NONE) { context.containingMod }

    for (traitOrImpl in lookup.findImplsAndTraits(type)) {
        val isInherent = traitOrImpl.isInherent

        for (member in traitOrImpl.implAndTraitExpandedMembers) {
            if (!nsFilter(member)) continue
            val name = member.name ?: continue

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
                bounds.any { processor(AssocItemScopeEntry(name, member, it.subst, type, traitOrImpl)) }
            } else {
                processor(AssocItemScopeEntry(name, member, emptySubstitution, type, traitOrImpl))
            }
            if (result) return true
        }
    }
    return false
}

private fun processAssociatedItemsWithSelfSubst(
    lookup: ImplLookup,
    path: RsPath,
    type: Ty,
    ns: Set<Namespace>,
    selfSubst: Substitution,
    processor: (AssocItemScopeEntry) -> Boolean
): Boolean {
    return processAssociatedItems(lookup, type, ns, path) {
        processor(it.copy(subst = it.subst + selfSubst))
    }
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
        val patternProcessor = createProcessor(processor.name) { e ->
            if (e.name !in alreadyProcessedNames) {
                alreadyProcessedNames += e.name
                processor(e)
            } else {
                false
            }
        }

        return PsiTreeUtil.findChildrenOfType(pattern, RsPatBinding::class.java).any { binding ->
            val name = binding.name ?: return@any false
            patternProcessor.lazy(name) {
                binding.takeIf { (it.parent is RsPatField || !it.isReferenceToConstant) && hygieneFilter(it) }
            }
        }
    }

    fun processCondition(condition: RsCondition?, processor: RsResolveProcessor): Boolean {
        if (condition == null || condition == cameFrom) return false
        val pat = condition.pat ?: return false
        return processPattern(pat, processor)
    }

    when (scope) {
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor, ipm = ipm)) return true
        }

        is RsTypeAlias -> {
            if (processAll(scope.typeParameters, processor)) return true
            if (Namespace.Values in ns && processAll(scope.constParameters, processor)) return true
        }

        is RsStructItem,
        is RsEnumItem,
        is RsTraitOrImpl -> {
            scope as RsGenericDeclaration
            if (processAll(scope.typeParameters, processor)) return true
            if (Namespace.Values in ns && processAll(scope.constParameters, processor)) return true
            if (processor("Self", scope)) return true
            if (scope is RsImplItem) {
                scope.traitRef?.let { traitRef ->
                    // really should be unnamed, but "_" is not a valid name in rust, so I think it's ok
                    if (processor.lazy("_") { traitRef.resolveToTrait() }) return true
                }
            }
        }

        is RsFunction -> {
            if (Namespace.Types in ns && processAll(scope.typeParameters, processor)) return true
            if (Namespace.Values in ns && processAll(scope.constParameters, processor)) return true
            // XXX: `cameFrom is RsBlock` prevents switches to AST in cases like `fn foo(a: usize, b: [u8; SIZE])`.
            // Note that rustc really process them and show [E0435] on this: `fn foo(a: usize, b: [u8; a])`.
            if (Namespace.Values in ns && cameFrom is RsBlock) {
                val selfParam = scope.selfParameter
                if (selfParam != null && processor("self", selfParam)) return true

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
            val visited = mutableSetOf<String>()
            if (Namespace.Values in ns) {
                val shadowingProcessor = createProcessor(processor.name) { e ->
                    (e.name !in visited) && processor(e).also {
                        if (e.isInitialized && e.element != null) {
                            visited += e.name
                        }
                    }
                }

                val letDecls = mutableListOf<RsLetDecl>()
                val stmts = when (scope) {
                    is RsBlock -> scope.expandedStmtsAndTailExpr.first
                    is RsReplCodeFragment -> scope.expandedStmtsAndTailExpr.first
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

            return processItemDeclarations(scope as RsItemsOwner, ns, processor, ItemProcessingMode.WITH_PRIVATE_IMPORTS)
        }

        is RsForExpr -> {
            if (scope.expr == cameFrom) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.pat
            if (pat != null && processPattern(pat, processor)) return true
        }

        is RsIfExpr -> {
            // else branch of 'if let' expression shouldn't look into condition pattern
            if (scope.elseBranch == cameFrom) return false
            return processCondition(scope.condition, processor)
        }
        is RsWhileExpr -> {
            return processCondition(scope.condition, processor)
        }

        is RsLambdaExpr -> {
            if (Namespace.Values !in ns) return false
            for (parameter in scope.valueParameters) {
                val pat = parameter.pat
                if (pat != null && processPattern(pat, processor)) return true
            }
        }

        is RsMatchArm -> {
            return processPattern(scope.pat, processor)
        }
    }
    return false
}

fun processNestedScopesUpwards(scopeStart: RsElement, ns: Set<Namespace>, processor: RsResolveProcessor): Boolean =
    processNestedScopesUpwards(scopeStart, ns, isCompletion = false, processor = processor)

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    ns: Set<Namespace>,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val hygieneFilter: (RsPatBinding) -> Boolean = if (scopeStart is RsPath && ns == VALUES) {
        makeHygieneFilter(scopeStart)
    } else {
        { true }
    }
    val prevScope = mutableMapOf<String, Set<Namespace>>()
    val stop = walkUp(scopeStart, { it is RsMod }) { cameFrom, scope ->
        processWithShadowingAndUpdateScope(prevScope, processor) { shadowingProcessor ->
            val ipm = when {
                scope !is RsMod -> ItemProcessingMode.WITH_PRIVATE_IMPORTS
                isCompletion -> ItemProcessingMode.WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES_COMPLETION
                else -> ItemProcessingMode.WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES
            }
            processLexicalDeclarations(scope, cameFrom, ns, hygieneFilter, ipm, shadowingProcessor)
        }
    }
    if (stop) return true

    // Prelude is injected via implicit star import `use std::prelude::v1::*;`
    if (processor(ScopeEvent.STAR_IMPORTS)) return false

    val prelude = findPrelude(scopeStart)
    if (prelude != null) {
        return processWithShadowing(prevScope, processor) { shadowingProcessor ->
            processItemDeclarations(prelude, ns, shadowingProcessor, ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS)
        }
    }

    return false
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
    processor: RsResolveProcessor,
    f: (RsResolveProcessor) -> Boolean
): Boolean {
    val currScope = mutableMapOf<String, Set<Namespace>>()
    val shadowingProcessor = createProcessor(processor.name) { e ->
        val prevNs = prevScope[e.name]
        if (prevNs != null && (e.element as? RsNamedElement)?.namespaces?.intersects(prevNs) == true) {
            return@createProcessor false
        }
        val result = processor(e)
        if (e.isInitialized) {
            val newNs = (e.element as? RsNamedElement)?.namespaces
            if (newNs != null) {
                currScope[e.name] = prevNs?.let { it + newNs } ?: newNs
            }
        }
        result
    }
    return try {
        f(shadowingProcessor)
    } finally {
        prevScope.putAll(currScope)
    }
}

inline fun processWithShadowing(
    prevScope: Map<String, Set<Namespace>>,
    originalProcessor: RsResolveProcessor,
    f: (RsResolveProcessor) -> Boolean
): Boolean {
    val processor = createProcessor(originalProcessor.name) { e ->
        val prevNs = prevScope[e.name]
        (prevNs == null || (e.element as? RsNamedElement)?.namespaces?.intersects(prevNs) != true)
            && originalProcessor(e)
    }
    return f(processor)
}

fun findPrelude(element: RsElement): RsFile? {
    val crateRoot = element.crateRoot as? RsFile ?: return null
    val cargoPackage = crateRoot.containingCargoPackage
    val isStdlib = cargoPackage?.origin == PackageOrigin.STDLIB && !element.isDoctestInjection
    val packageName = cargoPackage?.normName

    // `std` and `core` crates explicitly add their prelude
    // TODO `#[prelude_import]`
    val stdlibCrateRoot = if (isStdlib && (packageName == STD || packageName == CORE)) {
        crateRoot
    } else {
        implicitStdlibCrate(crateRoot)?.crateRoot
    }

    return stdlibCrateRoot
        ?.virtualFile
        ?.findFileByRelativePath("../prelude/v1.rs")
        ?.toPsiFile(element.project)
        ?.rustFile
}

// Implicit extern crate from stdlib
private fun implicitStdlibCrate(scope: RsFile): ImplicitStdlibCrate? {
    val (name, crateRoot) = when (scope.attributes) {
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
    val shadowingStdCrates = Testmark("shadowingStdCrates")
    val missingMacroUse = Testmark("missingMacroUse")
    val processSelfCrateExportedMacros = Testmark("processSelfCrateExportedMacros")
    val dollarCrateMagicIdentifier = Testmark("dollarCrateMagicIdentifier")
    val selfInGroup = Testmark("selfInGroup")
    val crateRootModule = Testmark("crateRootModule")
    val modRsFile = Testmark("modRsFile")
    val modDeclExplicitPathInInlineModule = Testmark("modDeclExplicitPathInInlineModule")
    val modDeclExplicitPathInNonInlineModule = Testmark("modDeclExplicitPathInNonInlineModule")
    val selfRelatedTypeSpecialCase = Testmark("selfRelatedTypeSpecialCase")
    val skipAssocTypeFromImpl = Testmark("skipAssocTypeFromImpl")
}

private data class ImplicitStdlibCrate(val name: String, val crateRoot: RsFile)

