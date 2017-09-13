/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.fixes.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsMembersImpl
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

class RsErrorAnnotator : Annotator, HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile): Boolean = file is RsFile

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RsVisitor() {
            override fun visitBaseType(o: RsBaseType) = checkBaseType(holder, o)
            override fun visitConstant(o: RsConstant) = checkDuplicates(holder, o)
            override fun visitValueArgumentList(o: RsValueArgumentList) = checkValueArgumentList(holder, o)
            override fun visitStructItem(o: RsStructItem) = checkDuplicates(holder, o)
            override fun visitEnumItem(o: RsEnumItem) = checkDuplicates(holder, o)
            override fun visitEnumVariant(o: RsEnumVariant) = checkDuplicates(holder, o)
            override fun visitFunction(o: RsFunction) = checkFunction(holder, o)
            override fun visitImplItem(o: RsImplItem) = checkImpl(holder, o)
            override fun visitLabel(o: RsLabel) = checkLabel(holder, o)
            override fun visitLifetime(o: RsLifetime) = checkLifetime(holder, o)
            override fun visitModDeclItem(o: RsModDeclItem) = checkModDecl(holder, o)
            override fun visitModItem(o: RsModItem) = checkDuplicates(holder, o)
            override fun visitPatBinding(o: RsPatBinding) = checkPatBinding(holder, o)
            override fun visitPath(o: RsPath) = checkPath(holder, o)
            override fun visitFieldDecl(o: RsFieldDecl) = checkDuplicates(holder, o)
            override fun visitRetExpr(o: RsRetExpr) = checkRetExpr(holder, o)
            override fun visitTraitItem(o: RsTraitItem) = checkDuplicates(holder, o)
            override fun visitTypeAlias(o: RsTypeAlias) = checkTypeAlias(holder, o)
            override fun visitTypeParameter(o: RsTypeParameter) = checkDuplicates(holder, o)
            override fun visitLifetimeParameter(o: RsLifetimeParameter) = checkDuplicates(holder, o)
            override fun visitVis(o: RsVis) = checkVis(holder, o)
            override fun visitBinaryExpr(o: RsBinaryExpr) = checkBinary(holder, o)
            override fun visitCallExpr(o: RsCallExpr) = checkCallExpr(holder, o)
            override fun visitMethodCall(o: RsMethodCall) = checkMethodCallExpr(holder, o)
            override fun visitUnaryExpr(o: RsUnaryExpr) = checkUnaryExpr(holder, o)
            override fun visitExternCrateItem(o: RsExternCrateItem) = checkExternCrate(holder, o)
            override fun visitDotExpr(o: RsDotExpr) = checkDotExpr(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkDotExpr(holder: AnnotationHolder, o: RsDotExpr) {
        val field = o.fieldLookup ?: o.methodCall ?: return
        checkReferenceIsPublic(field, o, holder)
    }

    private fun checkReferenceIsPublic(field: RsReferenceElement, o: PsiElement, holder: AnnotationHolder) {
        val ref = field.reference.resolve() as? RsVisibilityOwner ?: return
        if (ref.isPublic) return
        val refMod = ref.parentOfType<RsMod>() ?: return
        val oMod = o.parentOfType<RsMod>() ?: return
        if (refMod == oMod) return
        if (oMod.superMods.contains(refMod)) return
        val memberImpls = ref.parent as? RsMembersImpl
        if (memberImpls != null) {
            val parent = memberImpls.context ?: return
            when (parent) {
                is RsImplItem -> if (parent.traitRef != null) return
                is RsTraitItem -> return
            }
        }
        val (elem, desc) = when (field) {
            is RsFieldLookup -> field to "Attempted to access a private field on a struct. [E0616]"
            is RsMethodCall -> field.identifier to "A private item was used outside of its scope. [E0624]"
            is RsPath -> field to "A private item was used outside its scope. [E0603]"
            else -> return
        }
        holder.createErrorAnnotation(elem, desc)
    }

    private fun checkMethodCallExpr(holder: AnnotationHolder, o: RsMethodCall) {
        val fn = o.reference.resolve() as? RsFunction ?: return
        if (fn.isUnsafe) {
            checkUnsafeCall(holder, o.parentDotExpr)
        }
    }

    private fun checkCallExpr(holder: AnnotationHolder, o: RsCallExpr) {
        val path = (o.expr as? RsPathExpr)?.path ?: return
        val fn = path.reference.resolve() as? RsFunction ?: return
        if (fn.isUnsafe) {
            checkUnsafeCall(holder, o)
        }
    }

    private fun PsiElement.isInUnsafeBlockOrFn(parentsToSkip: Int = 0): Boolean {
        val parent = this.ancestors
            .drop(parentsToSkip)
            .find {
                when (it) {
                    is RsBlockExpr -> it.unsafe != null
                    is RsFunction -> true
                    else -> false
                }
            } ?: return false

        return parent is RsBlockExpr || (parent is RsFunction && parent.isUnsafe)
    }

    private fun checkUnsafeCall(holder: AnnotationHolder, o: RsExpr) {
        if (!o.isInUnsafeBlockOrFn(/* skip the expression itself*/ 1)) {
            val annotation = holder.createErrorAnnotation(o, "Call to unsafe function requires unsafe function or block [E0133]")
            annotation.registerFix(SurroundWithUnsafeFix(o))
            val block = o.parentOfType<RsBlock>()?.parent ?: return
            annotation.registerFix(AddUnsafeFix(block))
        }
    }

    private fun checkUnsafePtrDereference(holder: AnnotationHolder, o: RsUnaryExpr) {
        if (o.expr?.type !is TyPointer) return

        if (!o.isInUnsafeBlockOrFn()) {
            val annotation = holder.createErrorAnnotation(o, "Dereference of raw pointer requires unsafe function or block [E0133]")
            annotation.registerFix(SurroundWithUnsafeFix(o))
            val block = o.parentOfType<RsBlock>()?.parent ?: return
            annotation.registerFix(AddUnsafeFix(block))
        }
    }

    private fun checkUnaryExpr(holder: AnnotationHolder, unaryExpr: RsUnaryExpr) {
        if (unaryExpr.operatorType == UnaryOperator.DEREF) {
            checkUnsafePtrDereference(holder, unaryExpr)
        }
    }

    private fun checkBaseType(holder: AnnotationHolder, type: RsBaseType) {
        if (type.underscore == null) return
        val owner = type.owner.parent
        if ((owner is RsValueParameter || owner is RsRetType) && owner.parent.parent !is RsLambdaExpr || owner is RsConstant) {
            holder.createErrorAnnotation(type, "The type placeholder `_` is not allowed within types on item signatures [E0121]")
        }
    }

    private fun checkPatBinding(holder: AnnotationHolder, binding: RsPatBinding) {
        binding.parentOfType<RsValueParameterList>()?.let { checkDuplicates(holder, binding, it, recursively = true) }
    }

    private fun checkPath(holder: AnnotationHolder, path: RsPath) {
        val child = path.path
        if ((child == null || isValidSelfSuperPrefix(child)) && !isValidSelfSuperPrefix(path)) {
            holder.createErrorAnnotation(path, "Invalid path: self and super are allowed only at the beginning")
            return
        }

        if (path.self != null && path.parent !is RsPath) {
            val function = path.parentOfType<RsFunction>()
            if (function == null) {
                holder.createErrorAnnotation(path, "self value is not available in this context")
                return
            }

            if (function.selfParameter == null) {
                val error = "The self keyword was used in a static method [E0424]"
                val annotation = holder.createErrorAnnotation(path, error)
                if (function.owner.isImplOrTrait) {
                    annotation.registerFix(AddSelfFix(function))
                }
            }
        }
        checkReferenceIsPublic(path, path, holder)
    }

    private fun checkVis(holder: AnnotationHolder, vis: RsVis) {
        if (vis.parent is RsImplItem || vis.parent is RsForeignModItem || isInTraitImpl(vis) || isInEnumVariantField(vis)) {
            holder.createErrorAnnotation(vis, "Unnecessary visibility qualifier [E0449]")
        }
    }

    private fun checkLabel(holder: AnnotationHolder, label: RsLabel) =
        requireResolve(holder, label, "Use of undeclared label `${label.text}` [E0426]")

    private fun checkLifetime(holder: AnnotationHolder, lifetime: RsLifetime) {
        if (lifetime.isPredefined) return
        requireResolve(holder, lifetime, "Use of undeclared lifetime name `${lifetime.text}` [E0261]")
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RsModDeclItem) {
        checkDuplicates(holder, modDecl)
        val pathAttribute = modDecl.pathAttribute

        // mods inside blocks require explicit path  attribute
        // https://github.com/rust-lang/rust/pull/31534
        if (modDecl.isLocal && pathAttribute == null) {
            val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
            holder.createErrorAnnotation(modDecl, message)
            return
        }

        if (!modDecl.containingMod.ownsDirectory && pathAttribute == null) {
            // We don't want to show the warning if there is no cargo project
            // associated with the current module. Without it we can't know for
            // sure that a mod is not a directory owner.
            if (modDecl.cargoWorkspace != null) {
                holder.createErrorAnnotation(modDecl, "Cannot declare a new module at this location")
                    .registerFix(AddModuleFileFix(modDecl, expandModuleFirst = true))
            }
            return
        }

        if (modDecl.reference.resolve() == null) {
            holder.createErrorAnnotation(modDecl, "Unresolved module")
                .registerFix(AddModuleFileFix(modDecl, expandModuleFirst = false))
        }
    }

    private fun checkImpl(holder: AnnotationHolder, impl: RsImplItem) {
        val traitRef = impl.traitRef ?: return
        val trait = traitRef.resolveToTrait ?: return
        val traitName = trait.name ?: return
        when {
            impl.isUnsafe && impl.excl != null ->
                holder.createErrorAnnotation(traitRef, "Negative implementations are not unsafe [E0198]")

            impl.isUnsafe && !trait.isUnsafe ->
                holder.createErrorAnnotation(traitRef, "Implementing the trait `$traitName` is not unsafe [E0199]")

            !impl.isUnsafe && trait.isUnsafe && impl.excl == null ->
                holder.createErrorAnnotation(traitRef, "The trait `$traitName` requires an `unsafe impl` declaration [E0200]")
        }
        val implInfo = TraitImplementationInfo.create(trait, impl) ?: return

        if (implInfo.missingImplementations.isNotEmpty()) {
            val implHeaderTextRange = TextRange.create(
                impl.textRange.startOffset,
                impl.typeReference?.textRange?.endOffset ?: impl.textRange.endOffset
            )

            val missing = implInfo.missingImplementations.map { it.name }.namesList
            holder.createErrorAnnotation(implHeaderTextRange,
                "Not all trait items implemented, missing: $missing [E0046]"
            ).registerFix(ImplementMembersFix(impl))
        }

        for (member in implInfo.nonExistentInTrait) {
            holder.createErrorAnnotation(member.nameIdentifier!!,
                "Method `${member.name}` is not a member of trait `$traitName` [E0407]")
        }

        for ((imp, dec) in implInfo.implementationToDeclaration) {
            if (imp is RsFunction && dec is RsFunction) {
                checkTraitFnImplParams(holder, imp, dec, traitName)
            }
        }
    }

    private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
        checkDuplicates(holder, ta)
    }

    private fun checkTraitFnImplParams(holder: AnnotationHolder, fn: RsFunction, superFn: RsFunction, traitName: String) {
        val params = fn.valueParameterList ?: return
        val selfArg = fn.selfParameter

        if (selfArg != null && superFn.selfParameter == null) {
            holder.createErrorAnnotation(selfArg,
                "Method `${fn.name}` has a `${selfArg.canonicalDecl}` declaration in the impl, but not in the trait [E0185]")
        } else if (selfArg == null && superFn.selfParameter != null) {
            holder.createErrorAnnotation(params,
                "Method `${fn.name}` has a `${superFn.selfParameter?.canonicalDecl}` declaration in the trait, but not in the impl [E0186]")
        }

        val paramsCount = fn.valueParameters.size
        val superParamsCount = superFn.valueParameters.size
        if (paramsCount != superParamsCount) {
            holder.createErrorAnnotation(params,
                "Method `${fn.name}` has $paramsCount ${pluralise(paramsCount, "parameter", "parameters")} but the declaration in trait `$traitName` has $superParamsCount [E0050]")
        }
    }

    private fun checkBinary(holder: AnnotationHolder, o: RsBinaryExpr) {
        if (o.isComparisonBinaryExpr() && (o.left.isComparisonBinaryExpr() || o.right.isComparisonBinaryExpr())) {
            val annotator = holder.createErrorAnnotation(o, "Chained comparison operator require parentheses")
            annotator.registerFix(AddTurbofishFix())
        }
    }

    private fun checkValueArgumentList(holder: AnnotationHolder, args: RsValueArgumentList) {
        val parent = args.parent
        val (expectedCount, variadic) = when (parent) {
            is RsCallExpr -> parent.expectedParamsCount()
            is RsMethodCall -> parent.expectedParamsCount()
            else -> null
        } ?: return
        val realCount = args.exprList.size
        if (variadic && realCount < expectedCount) {
            holder.createErrorAnnotation(args,
                "This function takes at least $expectedCount ${pluralise(expectedCount, "parameter", "parameters")}"
                    + " but $realCount ${pluralise(realCount, "parameter", "parameters")}"
                    + " ${pluralise(realCount, "was", "were")} supplied [E0060]")
        } else if (!variadic && realCount != expectedCount) {
            holder.createErrorAnnotation(args,
                "This function takes $expectedCount ${pluralise(expectedCount, "parameter", "parameters")}"
                    + " but $realCount ${pluralise(realCount, "parameter", "parameters")}"
                    + " ${pluralise(realCount, "was", "were")} supplied [E0061]")
        }
    }

    private fun checkFunction(holder: AnnotationHolder, fn: RsFunction) {
        for (it in fn.inference.diagnostics) {
            it.addToHolder(holder)
        }
        checkDuplicates(holder, fn)
    }

    private fun checkRetExpr(holder: AnnotationHolder, ret: RsRetExpr) {
        if (ret.expr != null) return
        val fn = ret.ancestors.find { it is RsFunction || it is RsLambdaExpr } as? RsFunction ?: return
        val retType = fn.retType?.typeReference?.type ?: return
        if (retType is TyUnit) return
        holder.createErrorAnnotation(ret, "`return;` in a function whose return type is not `()` [E0069]")
    }

    private fun checkExternCrate(holder: AnnotationHolder, el: RsExternCrateItem) {
        if (el.reference.multiResolve().isNotEmpty() || el.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return
        holder.createErrorAnnotation(el.textRange, "Can't find crate for `${el.identifier.text}` [E0463]")
    }

    private fun requireResolve(holder: AnnotationHolder, el: RsReferenceElement, message: String) {
        if (el.reference.resolve() != null || el.reference.multiResolve().size > 1) return
        holder.createErrorAnnotation(el.textRange, message)
            .highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
    }


    private fun isInTraitImpl(o: RsVis): Boolean {
        val impl = o.parent?.parent?.parent
        return impl is RsImplItem && impl.traitRef != null
    }

    private fun isInEnumVariantField(o: RsVis): Boolean {
        val field = o.parent as? RsFieldDecl
            ?: o.parent as? RsTupleFieldDecl
            ?: return false
        return field.parent.parent is RsEnumVariant
    }

    private fun pluralise(count: Int, singular: String, plural: String): String =
        if (count == 1) singular else plural

    private val Collection<String?>.namesList: String
        get() = mapNotNull { "`$it`" }.joinToString(", ")

    private val RsSelfParameter.canonicalDecl: String
        get() = buildString {
            if (isRef) append('&')
            if (mutability.isMut) append("mut ")
            append("self")
        }
}

private fun RsExpr?.isComparisonBinaryExpr(): Boolean {
    val op = this as? RsBinaryExpr ?: return false
    return op.operatorType is ComparisonOp
}

private fun checkDuplicates(holder: AnnotationHolder, element: RsNameIdentifierOwner, scope: PsiElement = element.parent, recursively: Boolean = false) {
    val owner = if (scope is RsMembers) scope.parent else scope
    val duplicates = holder.currentAnnotationSession.duplicatesByNamespace(scope, recursively)
    val ns = element.namespaces.find { element in duplicates[it].orEmpty() }
        ?: return
    val name = element.name!!
    val message = when {
        element is RsFieldDecl -> "Field `$name` is already declared [E0124]"
        element is RsEnumVariant -> "Enum variant `$name` is already declared [E0428]"
        element is RsLifetimeParameter -> "Lifetime name `$name` declared twice in the same scope [E0263]"
        element is RsPatBinding && owner is RsValueParameterList -> "Identifier `$name` is bound more than once in this parameter list [E0415]"
        element is RsTypeParameter -> "The name `$name` is already used for a type parameter in this type parameter list [E0403]"
        owner is RsImplItem -> "Duplicate definitions with name `$name` [E0201]"
        else -> {
            val scopeType = when (owner) {
                is RsBlock -> "block"
                is RsMod, is RsForeignModItem -> "module"
                is RsTraitItem -> "trait"
                else -> "scope"
            }
            "A ${ns.itemName} named `$name` has already been defined in this $scopeType [E0428]"
        }
    }

    holder.createErrorAnnotation(element.nameIdentifier ?: element, message)
}


private fun AnnotationSession.duplicatesByNamespace(owner: PsiElement, recursively: Boolean): Map<Namespace, Set<PsiElement>> {
    val fileMap = fileDuplicatesMap()
    fileMap[owner]?.let { return it }

    val duplicates: Map<Namespace, Set<PsiElement>> =
        owner.namedChildren(recursively)
            .filter { it !is RsExternCrateItem } // extern crates can have aliases.
            .filter { it.name != null }
            .flatMap { it.namespaced }
            .groupBy { it.first }       // Group by namespace
            .map { entry ->
                val (namespace, items) = entry
                namespace to items.asSequence()
                    .map { it.second }
                    .groupBy { it.name }
                    .map { it.value }
                    .filter {
                        it.size > 1 &&
                            it.any { !(it is RsDocAndAttributeOwner && it.queryAttributes.hasCfgAttr()) }
                    }
                    .flatten()
                    .toSet()
            }
            .toMap()

    fileMap[owner] = duplicates
    return duplicates
}

private fun PsiElement.namedChildren(recursively: Boolean): Sequence<RsNamedElement> =
    if (recursively) {
        descendantsOfType<RsNamedElement>().asSequence()
    } else {
        children.asSequence().filterIsInstance<RsNamedElement>()
    }

private val DUPLICATES_BY_SCOPE = Key<MutableMap<
    PsiElement,
    Map<Namespace, Set<PsiElement>>>>("org.rust.ide.annotator.RsErrorAnnotator.duplicates")

private fun AnnotationSession.fileDuplicatesMap(): MutableMap<PsiElement, Map<Namespace, Set<PsiElement>>> {
    var map = getUserData(DUPLICATES_BY_SCOPE)
    if (map == null) {
        map = mutableMapOf()
        putUserData(DUPLICATES_BY_SCOPE, map)
    }
    return map
}

private val RsNamedElement.namespaced: Sequence<Pair<Namespace, RsNamedElement>>
    get() = namespaces.asSequence().map { Pair(it, this) }

private fun RsCallExpr.expectedParamsCount(): Pair<Int, Boolean>? {
    val path = (expr as? RsPathExpr)?.path ?: return null
    val el = path.reference.resolve()
    if (el is RsDocAndAttributeOwner && el.queryAttributes.hasCfgAttr()) return null
    return when (el) {
        is RsFieldsOwner -> el.tupleFields?.tupleFieldDeclList?.size?.let { Pair(it, false) }
        is RsFunction -> {
            val owner = el.owner
            if (owner.isTraitImpl) return null
            val count = el.valueParameterList?.valueParameterList?.size ?: return null
            // We can call foo.method(1), or Foo::method(&foo, 1), so need to take coloncolon into account
            val s = if (path.coloncolon != null && el.selfParameter != null) 1 else 0
            Pair(count + s, el.isVariadic)
        }
        else -> null
    }
}

private fun RsMethodCall.expectedParamsCount(): Pair<Int, Boolean>? {
    val fn = reference.resolve() as? RsFunction ?: return null
    if (fn.queryAttributes.hasCfgAttr()) return null
    return fn.valueParameterList?.valueParameterList?.size?.let { Pair(it, fn.isVariadic) }
        .takeIf { fn.owner.isInherentImpl }
}

private fun isValidSelfSuperPrefix(path: RsPath): Boolean {
    if (path.self == null && path.`super` == null) return true
    if (path.path == null && path.coloncolon != null) return false
    if (path.self != null && path.path != null) return false
    if (path.`super` != null) {
        val q = path.path ?: return true
        return q.self != null || q.`super` != null
    }
    return true
}
