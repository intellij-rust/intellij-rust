/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("LoopToCallChain")

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.NameResolutionTestmarks.missingMacroExport
import org.rust.lang.core.resolve.NameResolutionTestmarks.missingMacroUse
import org.rust.lang.core.resolve.NameResolutionTestmarks.selfInGroup
import org.rust.lang.core.resolve.ref.MethodCallee
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.infer.foldTyTypeParameterWith
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark
import org.rust.openapiext.toPsiFile

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

fun processFieldExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    isCompletion: Boolean,
    processor: RsResolveProcessor
): Boolean {
    for (ty in lookup.coercionSequence(receiverType)) {
        if (ty !is TyStruct) continue
        if (processFieldDeclarations(ty.item, processor)) return true
    }
    if (isCompletion && processMethodDeclarationsWithDeref(lookup, receiverType, processor)) {
        return true
    }
    return false
}

fun processStructLiteralFieldResolveVariants(field: RsStructLiteralField, processor: RsResolveProcessor): Boolean {
    var resolved = field.parentStructLiteral.path.reference.resolve()

    // Resolve potential type aliases
    while (resolved is RsTypeAlias) {
        resolved = (resolved.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve()
    }
    val structOrEnumVariant = resolved as? RsFieldsOwner ?: return false
    return processFieldDeclarations(structOrEnumVariant, processor)
}

fun processMethodCallExprResolveVariants(lookup: ImplLookup, receiverType: Ty, processor: RsMethodResolveProcessor): Boolean =
    processMethodDeclarationsWithDeref(lookup, receiverType, processor)

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
fun processModDeclResolveVariants(modDecl: RsModDeclItem, processor: RsResolveProcessor): Boolean {
    val dir = modDecl.containingMod.ownedDirectory ?: return false

    val explicitPath = modDecl.pathAttribute
    if (explicitPath != null) {
        val vFile = dir.virtualFile.findFileByRelativePath(explicitPath.replace('\\', '/')) ?: return false
        val mod = vFile.toPsiFile(modDecl.project)?.rustMod ?: return false

        val name = modDecl.name ?: return false
        return processor(name, mod)
    }
    if (modDecl.isLocal) return false

    for (file in dir.files) {
        if (file == modDecl.contextualFile.originalFile || file.name == RsMod.MOD_RS) continue
        val mod = file.rustMod ?: continue
        val fileName = FileUtil.getNameWithoutExtension(file.name)
        val modDeclName = modDecl.referenceName
        // Handle case-insensitive filesystem (windows)
        val name = if (modDeclName.toLowerCase() == fileName.toLowerCase()) {
            modDeclName
        } else {
            fileName
        }
        if (processor(name, mod)) return true
    }

    for (d in dir.subdirectories) {
        val mod = d.findFile(RsMod.MOD_RS)?.rustMod ?: continue
        if (processor(d.name, mod)) return true
    }

    return false
}

fun processExternCrateResolveVariants(crate: RsExternCrateItem, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    val pkg = crate.containingCargoPackage ?: return false

    val visitedDeps = mutableSetOf<String>()
    fun processPackage(pkg: CargoWorkspace.Package): Boolean {
        if (isCompletion && pkg.origin != PackageOrigin.DEPENDENCY) return false
        val libTarget = pkg.libTarget ?: return false

        if (pkg.origin == PackageOrigin.STDLIB && pkg.name in visitedDeps) return false
        visitedDeps += pkg.name
        return processor.lazy(libTarget.normName) {
            libTarget.crateRoot?.toPsiFile(crate.project)?.rustMod
        }
    }

    if (processPackage(pkg)) return true
    val explicitDepsFirst = pkg.dependencies.sortedBy {
        when (it.origin) {
            PackageOrigin.WORKSPACE,
            PackageOrigin.DEPENDENCY,
            PackageOrigin.TRANSITIVE_DEPENDENCY -> {
                NameResolutionTestmarks.shadowingStdCrates.hit()
                0
            }
            PackageOrigin.STDLIB -> 1
        }
    }
    for (p in explicitDepsFirst) {
        if (processPackage(p)) return true
    }
    return false
}

private val RsPath.qualifier: RsPath? get() {
    path?.let { return it }
    var ctx = context
    while (ctx is RsPath) {
        ctx = ctx.context
    }
    return (ctx as? RsUseSpeck)?.qualifier
}

fun processPathResolveVariants(lookup: ImplLookup, path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {

    val qualifier = path.qualifier
    val typeQual = path.typeQual
    val parent = path.context
    val ns = when (parent) {
        is RsPath, is RsTypeElement, is RsTraitRef, is RsStructLiteral -> TYPES
        is RsUseSpeck -> if (parent.isStarImport) TYPES else TYPES_N_VALUES
        is RsPathExpr -> if (isCompletion) TYPES_N_VALUES else VALUES
        else -> TYPES_N_VALUES
    }

    if (qualifier != null) {
        val primitiveType = TyPrimitive.fromPath(qualifier)
        if (primitiveType != null) {
            if (processAssociatedItems(lookup, primitiveType, ns, processor)) return true
        }

        val (base, subst) = qualifier.reference.advancedResolve() ?: return false
        if (base is RsMod) {
            val s = base.`super`
            if (s != null && processor("super", s)) return true
        }
        if (parent is RsUseSpeck && path.path == null) {
            selfInGroup.hit()
            if (processor("self", base)) return true
        }
        if (processItemOrEnumVariantDeclarations(base, ns, processor, isSuperChain(qualifier))) return true
        if (base is RsTypeDeclarationElement && parent !is RsUseSpeck) {
            // Foo::<Bar>::baz
            val selfTy = run {
                val realSubst = if (qualifier.typeArgumentList != null) {
                    // If the path contains explicit type arguments `Foo::<_, Bar, _>::baz`
                    // it means that all possible `TyInfer` has already substituted (with `_`)
                    subst
                } else {
                    subst.mapValues { (_, v) -> v.foldTyTypeParameterWith { TyInfer.TyVar(it) } }
                }
                base.declaredType.substitute(realSubst)
            }
            val isSelf = qualifier.hasColonColon || !qualifier.hasCself
            val selfSubst = if (isSelf && base !is RsTraitItem) {
                mapOf(TyTypeParameter.self() to selfTy)
            } else {
                emptySubstitution
            }
            val assocItemsProcessor: RsResolveProcessor = {
                processor(it.name, it.element!!, it.subst + selfSubst)
            }
            if (processAssociatedItems(lookup, selfTy, ns, assocItemsProcessor)) return true
        }
        return false
    }

    if (typeQual != null) {
        // <T as Trait>::Item
        val trait = typeQual.traitRef?.resolveToBoundTrait ?: return false
        val selfSubst = mapOf(TyTypeParameter.self() to typeQual.typeReference.type)
        val subst = trait.subst.substituteInValues(selfSubst) + selfSubst
        if (processAllWithSubst(trait.element.members?.typeAliasList.orEmpty(), subst, processor)) return true
    }

    val containingMod = path.containingMod
    val crateRoot = path.crateRoot
    if (!path.hasColonColon) {
        if (Namespace.Types in ns) {
            if (processor("self", containingMod)) return true
            val superMod = containingMod.`super`
            if (superMod != null && processor("super", superMod)) return true
        }
    }

    // Paths in use items are implicitly global.
    if (path.hasColonColon || path.contextStrict<RsUseItem>() != null) {
        if (crateRoot != null) {
            if (processItemOrEnumVariantDeclarations(crateRoot, ns, processor)) return true
        }
        return false
    }

    return processNestedScopesUpwards(path, processor, ns)
}

fun processPatBindingResolveVariants(binding: RsPatBinding, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    return processNestedScopesUpwards(binding, { entry ->
        processor.lazy(entry.name) {
            val element = entry.element
            val isConstant = element is RsConstant
                || (element is RsEnumVariant && element.blockFields == null && element.tupleFields == null)
            val isPathOrDestructable = when (element) {
                is RsMod, is RsEnumItem, is RsEnumVariant, is RsStructItem -> true
                else -> false
            }
            if (isConstant || (isCompletion && isPathOrDestructable)) element else null
        }
    }, if (isCompletion) TYPES_N_VALUES else VALUES)
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
    loop@ for (scope in lifetime.ancestors) {
        val lifetimeParameters = when (scope) {
            is RsGenericDeclaration -> scope.typeParameterList?.lifetimeParameterList
            is RsWhereClause -> scope.wherePredList.mapNotNull { it.forLifetimes }.flatMap { it.lifetimeParameterList }
            is RsForInType -> scope.forLifetimes.lifetimeParameterList
            is RsPolybound -> scope.forLifetimes?.lifetimeParameterList
            else -> continue@loop
        }
        if (processAll(lifetimeParameters.orEmpty(), processor)) return true
    }
    return false
}

fun processLocalVariables(place: RsElement, processor: (RsPatBinding) -> Unit) {
    walkUp(place, { it is RsItemElement }) { cameFrom, scope ->
        processLexicalDeclarations(scope, cameFrom, VALUES) { v ->
            val el = v.element
            if (el is RsPatBinding) processor(el)
            true
        }
    }
}

/**
 * Resolves an absolute path.
 */
fun resolveStringPath(path: String, workspace: CargoWorkspace, project: Project): Pair<RsNamedElement, CargoWorkspace.Package>? {
    val parts = path.split("::", limit = 2)
    if (parts.size != 2) return null
    val pkg = workspace.findPackage(parts[0]) ?: return null

    val el = pkg.targets.asSequence()
        .mapNotNull { RsCodeFragmentFactory(project).createCrateRelativePath("::${parts[1]}", it) }
        .mapNotNull { it.reference.resolve() }
        .filterIsInstance<RsNamedElement>()
        .firstOrNull() ?: return null
    return el to pkg
}

fun processMacroReferenceVariants(ref: RsMacroReference, processor: RsResolveProcessor): Boolean {
    val definition = ref.ancestorStrict<RsMacroDefinitionCase>() ?: return false
    val simple = definition.macroPattern.descendantsOfType<RsMacroBinding>()
        .toList()

    return simple.any { processor(it) }
}

fun processMetaItemResolveVariants(element: RsMetaItem, processor: RsResolveProcessor): Boolean {
    if (element.ancestorStrict<RsMetaItem>()?.referenceName != "derive") return false
    val traitName = element.referenceName
    val traits = RsNamedElementIndex.findDerivableTraits(element.project, traitName)
    return processAll(traits, processor)
}

fun processMacroCallVariants(invocation: PsiElement, processor: RsResolveProcessor): Boolean =
    walkUp(invocation, { false }, false) { _, scope -> processMacroDeclarations(scope, processor) }

fun processMacroDeclarations(scope: RsElement, processor: RsResolveProcessor): Boolean {
    when (scope) {
        is RsFile -> if (processItemMacroDeclarations(scope, processor, scope.isCrateRoot)) return true
        is RsMod -> if (processItemMacroDeclarations(scope, processor)) return true
        is RsBlock -> if (processItemMacroDeclarations(scope, processor)) return true
    }
    return false
}

fun processBinaryOpVariants(element: RsBinaryOp, operator: OverloadableBinaryOperator,
                            processor: RsResolveProcessor): Boolean {
    val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return false
    val rhsType = binaryExpr.right?.type ?: return false
    val lhsType = binaryExpr.left.type
    val lookup = ImplLookup.relativeTo(element)
    val function = lookup.findOverloadedOpImpl(lhsType, rhsType, operator)
        ?.members
        ?.functionList
        ?.find { it.name == operator.fnName }
        ?: return false
    return processor(function)
}

val RsFile.exportedCrateMacros: List<RsMacroDefinition>
    get() = CachedValuesManager.getCachedValue(this) {
        val macros = exportedCrateMacros(this, true)
        CachedValueProvider.Result.create(macros, PsiModificationTracker.MODIFICATION_COUNT)
    }

fun Testmark.hitOnFalse(b: Boolean): Boolean {
    if (!b) hit()
    return b
}

private fun exportedCrateMacros(scope: RsItemsOwner, needExport: Boolean): List<RsMacroDefinition> {
    val result = mutableListOf<RsMacroDefinition>()
    loop@ for (item in scope.itemsAndMacros) {
        when (item) {
            is RsMacroDefinition ->
                if (!needExport || missingMacroExport.hitOnFalse(item.hasMacroExport)) result += item

            is RsExternCrateItem ->
                if (needExport) {
                    val reexport = item.findOuterAttr("macro_reexport")
                        ?.metaItem?.metaItemArgs?.metaItemList
                        ?.mapNotNull { it.referenceName } ?: continue@loop
                    val mod = item.reference.resolve() as? RsFile ?: continue@loop
                    val internalMacros = mod.exportedCrateMacros
                    result.addAll(internalMacros.filter { reexport.contains(it.name) })
                } else if (missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    val mod = item.reference.resolve() as? RsFile ?: continue@loop
                    result.addAll(mod.exportedCrateMacros)
                }

            is RsModDeclItem ->
                if (needExport || missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    val mod = item.reference.resolve() ?: continue@loop
                    if (mod is RsMod) {
                        result.addAll(exportedCrateMacros(mod, needExport))
                    }
                }

            is RsModItem ->
                if (needExport || missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    result.addAll(exportedCrateMacros(item, needExport))
                }
        }
    }
    return result
}

private fun processItemMacroDeclarations(
    scope: RsItemsOwner,
    processor: RsResolveProcessor,
    addStdCrate: Boolean = false
): Boolean {
    val macros = exportedCrateMacros(scope, false)
    if (processAll(macros, processor)) return true

    if (addStdCrate) {
        val prelude = scope.containingCargoPackage?.findDependency("std")
            ?.crateRoot
            ?.toPsiFile(scope.project)
            ?.rustMod
        if (prelude is RsFile && processAll(prelude.exportedCrateMacros, processor)) return true
    }
    return false
}

private fun processFieldDeclarations(struct: RsFieldsOwner, processor: RsResolveProcessor): Boolean {
    if (processAll(struct.namedFields, processor)) return true

    for ((idx, field) in struct.positionalFields.withIndex()) {
        if (processor(idx.toString(), field)) return true
    }
    return false
}

private fun processMethodDeclarationsWithDeref(lookup: ImplLookup, receiver: Ty, processor: RsMethodResolveProcessor): Boolean {
    return lookup.coercionSequence(receiver).withIndex().any { (i, ty) ->
        val methodProcessor: (AssocItemScopeEntry) -> Boolean = { (name, element, subst, impl) ->
            assert(subst.isEmpty())
            element is RsFunction && !element.isAssocFn && processor(MethodCallee(name, element, impl, ty, i))
        }
        processAssociatedItems(lookup, ty, VALUES, methodProcessor)
    }
}

private fun processAssociatedItems(lookup: ImplLookup, type: Ty, ns: Set<Namespace>, processor: (AssocItemScopeEntry) -> Boolean): Boolean {
    val visitedInherent = mutableSetOf<String>()
    fun processTraitOrImpl(traitOrImpl: RsTraitOrImpl, inherent: Boolean): Boolean {
        fun inherentProcessor(entry: RsNamedElement, impl: RsImplItem?): Boolean {
            val name = entry.name ?: return false
            if (inherent) visitedInherent.add(name)
            if (!inherent && name in visitedInherent) return false
            return processor(AssocItemScopeEntry(name, entry, emptySubstitution, impl))
        }

        /**
         * For `impl T for Foo`, this'll walk impl members and trait `T` members,
         * which are not implemented.
         */
        fun processMembersWithDefaults(accessor: (RsMembers) -> List<RsNamedElement>): Boolean {
            val impl = traitOrImpl as? RsImplItem
            val directlyImplemented = traitOrImpl.members?.let { accessor(it) }.orEmpty()
            if (directlyImplemented.any { inherentProcessor(it, impl) }) return true

            if (traitOrImpl is RsImplItem) {
                val direct = directlyImplemented.map { it.name }.toSet()
                val membersFromTrait = traitOrImpl.implementedTrait?.element?.members ?: return false
                for (member in accessor(membersFromTrait)) {
                    if (member.name !in direct && inherentProcessor(member, impl)) return true
                }
            }

            return false
        }

        if (Namespace.Values in ns) {
            if (processMembersWithDefaults({ it.functionList })) return true
            if (processMembersWithDefaults({ it.constantList })) return true
        }
        if (Namespace.Types in ns) {
            if (processMembersWithDefaults({ it.typeAliasList })) return true
        }
        return false
    }

    val (traits, inherent) = lookup.findImplsAndTraits(type).partition { (it as? RsImplItem)?.traitRef != null }
    if (inherent.any { processTraitOrImpl(it, true) }) return true
    if (traits.any { processTraitOrImpl(it, false) }) return true
    return false
}

private fun processLexicalDeclarations(scope: RsElement, cameFrom: PsiElement, ns: Set<Namespace>, processor: RsResolveProcessor): Boolean {
    check(cameFrom.context == scope)

    fun processPattern(pattern: RsPat, processor: RsResolveProcessor): Boolean {
        val boundNames = PsiTreeUtil.findChildrenOfType(pattern, RsPatBinding::class.java)
            .filter { it.reference.resolve() == null }
        return processAll(boundNames, processor)
    }

    fun processCondition(condition: RsCondition?, processor: RsResolveProcessor): Boolean {
        if (condition == null || condition == cameFrom) return false
        val pat = condition.pat
        if (pat != null && processPattern(pat, processor)) return true
        return false
    }

    when (scope) {
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor, withPrivateImports = true)) return true
        }

        is RsStructItem,
        is RsEnumItem,
        is RsTypeAlias -> {
            scope as RsGenericDeclaration
            if (processAll(scope.typeParameters, processor)) return true
        }

        is RsTraitOrImpl -> {
            if (processAll(scope.typeParameters, processor)) return true
            if (processor("Self", scope)) return true
        }

        is RsFunction -> {
            if (Namespace.Types in ns) {
                if (processAll(scope.typeParameters, processor)) return true
            }
            if (Namespace.Values in ns) {
                val selfParam = scope.selfParameter
                if (selfParam != null && processor("self", selfParam)) return true

                for (parameter in scope.valueParameters) {
                    val pat = parameter.pat ?: continue
                    if (processPattern(pat, processor)) return true
                }
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
            val visited = mutableSetOf<String>()
            if (Namespace.Values in ns) {
                val shadowingProcessor = { e: ScopeEntry ->
                    (e.name !in visited) && run {
                        visited += e.name
                        processor(e)
                    }
                }

                for (stmt in scope.stmtList.asReversed()) {
                    val pat = (stmt as? RsLetDecl)?.pat ?: continue
                    if (PsiUtilCore.compareElementsByPosition(cameFrom, stmt) < 0) continue
                    if (stmt == cameFrom) continue
                    if (processPattern(pat, shadowingProcessor)) return true
                }
            }

            return processItemDeclarations(scope, ns, processor, withPrivateImports = true)
        }

        is RsForExpr -> {
            if (scope.expr == cameFrom) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.pat
            if (pat != null && processPattern(pat, processor)) return true
        }

        is RsIfExpr -> {
            if (Namespace.Values !in ns) return false
            // else branch of 'if let' expression shouldn't look into condition pattern
            if (scope.elseBranch == cameFrom) return false
            return processCondition(scope.condition, processor)
        }
        is RsWhileExpr -> {
            if (Namespace.Values !in ns) return false
            return processCondition(scope.condition, processor)
        }

        is RsLambdaExpr -> {
            if (Namespace.Values !in ns) return false
            for (parameter in scope.valueParameterList.valueParameterList) {
                val pat = parameter.pat
                if (pat != null && processPattern(pat, processor)) return true
            }
        }

        is RsMatchArm -> {
            // Rust allows to defined several patterns in the single match arm,
            // but they all must bind the same variables, hence we can inspect
            // only the first one.
            if (cameFrom in scope.patList) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.patList.firstOrNull()
            if (pat != null && processPattern(pat, processor)) return true

        }
    }
    return false
}

private fun processNestedScopesUpwards(scopeStart: RsElement, processor: RsResolveProcessor, ns: Set<Namespace>): Boolean {
    val prevScope = mutableSetOf<String>()
    walkUp(scopeStart, { it is RsMod }) { cameFrom, scope ->
        val currScope = mutableListOf<String>()
        val shadowingProcessor = { e: ScopeEntry ->
            e.name !in prevScope && run {
                currScope += e.name
                processor(e)
            }
        }
        if (processLexicalDeclarations(scope, cameFrom, ns, shadowingProcessor)) return@walkUp true
        prevScope.addAll(currScope)
        false
    }

    val prelude = scopeStart.containingCargoPackage?.findDependency("std")?.crateRoot
        ?.findFileByRelativePath("../prelude/v1.rs")
        ?.toPsiFile(scopeStart.project)
        ?.rustMod
    if (prelude != null && processItemDeclarations(prelude, ns, { v -> v.name !in prevScope && processor(v) }, false)) return true

    return false
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

fun isSuperChain(path: RsPath): Boolean {
    val qual = path.path
    return (path.referenceName == "super" || path.referenceName == "self") && (qual == null || isSuperChain(qual))
}


object NameResolutionTestmarks {
    val shadowingStdCrates = Testmark("shadowingStdCrates")
    val missingMacroExport = Testmark("missingMacroExport")
    val missingMacroUse = Testmark("missingMacroUse")
    val selfInGroup = Testmark("selfInGroup")
    val selfInGroupName = Testmark("selfInGroupName")
}
