package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.ide.annotator.fixes.AddModuleFileFix
import org.rust.ide.annotator.fixes.AddSelfFix
import org.rust.ide.annotator.fixes.ImplementMethodsFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.descendantsOfType
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.symbols.RustPath

class RsErrorAnnotator : Annotator, HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile): Boolean = file is RsFile

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RsVisitor() {
            override fun visitConstant(o: RsConstant) = checkConstant(holder, o)
            override fun visitValueArgumentList(o: RsValueArgumentList) = checkValueArgumentList(holder, o)
            override fun visitStructItem(o: RsStructItem) = checkStructItem(holder, o)
            override fun visitEnumItem(o: RsEnumItem) = checkDuplicates(holder, o)
            override fun visitEnumVariant(o: RsEnumVariant) = checkDuplicates(holder, o)
            override fun visitFunction(o: RsFunction) = checkFunction(holder, o)
            override fun visitImplItem(o: RsImplItem) = checkImpl(holder, o)
            override fun visitModDeclItem(o: RsModDeclItem) = checkModDecl(holder, o)
            override fun visitModItem(o: RsModItem) = checkDuplicates(holder, o)
            override fun visitPatBinding(o: RsPatBinding) = checkPatBinding(holder, o)
            override fun visitPath(o: RsPath) = checkPath(holder, o)
            override fun visitFieldDecl(o: RsFieldDecl) = checkDuplicates(holder, o)
            override fun visitRefLikeType(o: RsRefLikeType) = checkRefLikeType(holder, o)
            override fun visitTraitItem(o: RsTraitItem) = checkDuplicates(holder, o)
            override fun visitTypeAlias(o: RsTypeAlias) = checkTypeAlias(holder, o)
            override fun visitTypeParameter(o: RsTypeParameter) = checkDuplicates(holder, o)
            override fun visitLifetimeParameter(o: RsLifetimeParameter) = checkDuplicates(holder, o)
            override fun visitValueParameterList(o: RsValueParameterList) = checkValueParameterList(holder, o)
            override fun visitValueParameter(o: RsValueParameter) = checkValueParameter(holder, o)
            override fun visitVis(o: RsVis) = checkVis(holder, o)
            override fun visitBinaryExpr(o: RsBinaryExpr) = checkBinary(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkPatBinding(holder: AnnotationHolder, binding: RsPatBinding) {
        binding.parentOfType<RsValueParameterList>()?.let { checkDuplicates(holder, binding, it, recursively = true) }
    }

    private fun checkPath(holder: AnnotationHolder, path: RsPath) {
        val child = path.path
        if ((child == null || child.asRustPath != null) && path.asRustPath == null) {
            holder.createErrorAnnotation(path, "Invalid path: self and super are allowed only at the beginning")
        }

        path.self ?: return
        val function = path.parentOfType<RsFunction>() ?: return
        val rustPath = path.asRustPath
        if (rustPath != null && rustPath !is RustPath.ModRelative && function.isAssocFn) {
            val error = "The self keyword was used in a static method [E0424]"
            val annotation = holder.createErrorAnnotation(path, error)
            if (function.role != RsFunctionRole.FREE) {
                annotation.registerFix(AddSelfFix(function))
            }
        }
    }

    private fun checkVis(holder: AnnotationHolder, vis: RsVis) {
        if (vis.parent is RsImplItem || vis.parent is RsForeignModItem || isInTraitImpl(vis) || isInEnumVariantField(vis)) {
            holder.createErrorAnnotation(vis, "Unnecessary visibility qualifier [E0449]")
        }
    }

    private fun checkConstant(holder: AnnotationHolder, const: RsConstant) {
        checkDuplicates(holder, const)
        val title = if (const.static != null) "Static constant `${const.identifier.text}`" else "Constant `${const.identifier.text}`"
        when (const.role) {
            RsConstantRole.FREE -> {
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                require(const.expr, holder, "$title must have a value", const)
            }
            RsConstantRole.TRAIT_CONSTANT -> {
                deny(const.vis, holder, "$title cannot have the `pub` qualifier")
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                deny(const.static, holder, "Static constants are not allowed in traits")
            }
            RsConstantRole.IMPL_CONSTANT -> {
                deny(const.static, holder, "Static constants are not allowed in impl blocks")
                require(const.expr, holder, "$title must have a value", const)
            }
            RsConstantRole.FOREIGN -> {
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                require(const.static, holder, "Only static constants are allowed in extern blocks", const.const)
                deny(const.expr, holder, "Static constants in extern blocks cannot have values", const.eq, const.expr)
            }
        }
    }

    private fun checkValueParameter(holder: AnnotationHolder, param: RsValueParameter) {
        val fn = param.parent.parent as? RsFunction ?: return
        when (fn.role) {
            RsFunctionRole.FREE,
            RsFunctionRole.IMPL_METHOD,
            RsFunctionRole.FOREIGN -> {
                require(param.pat, holder, "${fn.title} cannot have anonymous parameters", param)
            }
            RsFunctionRole.TRAIT_METHOD -> {
                denyType<RsPatTup>(param.pat, holder, "${fn.title} cannot have tuple parameters", param)
            }
        }
    }

    private fun checkValueParameterList(holder: AnnotationHolder, params: RsValueParameterList) {
        val fn = params.parent as? RsFunction ?: return
        when (fn.role) {
            RsFunctionRole.FREE -> {
                deny(params.selfParameter, holder, "${fn.title} cannot have `self` parameter")
                deny(params.dotdotdot, holder, "${fn.title} cannot be variadic")
            }
            RsFunctionRole.TRAIT_METHOD,
            RsFunctionRole.IMPL_METHOD -> {
                deny(params.dotdotdot, holder, "${fn.title} cannot be variadic")
            }
            RsFunctionRole.FOREIGN -> {
                deny(params.selfParameter, holder, "${fn.title} cannot have `self` parameter")
                checkDot3Parameter(holder, params.dotdotdot)
            }
        }
    }

    private fun checkDot3Parameter(holder: AnnotationHolder, dot3: PsiElement?) {
        if (dot3 == null) return
        dot3.rightVisibleLeaves
            .first {
                if (it.text != ")") {
                    holder.createErrorAnnotation(it, "`...` must be last in argument list for variadic function")
                }
                return
            }
    }

    private fun checkFunction(holder: AnnotationHolder, fn: RsFunction) {
        checkDuplicates(holder, fn)
        when (fn.role) {
            RsFunctionRole.FREE -> {
                require(fn.block, holder, "${fn.title} must have a body", fn.lastChild)
                deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
            }
            RsFunctionRole.TRAIT_METHOD -> {
                deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
                deny(fn.vis, holder, "${fn.title} cannot have the `pub` qualifier")
                deny(fn.const, holder, "Trait functions cannot be declared const [E0379]")
            }
            RsFunctionRole.IMPL_METHOD -> {
                require(fn.block, holder, "${fn.title} must have a body", fn.lastChild)
                if (fn.default != null) {
                    deny(fn.vis, holder, "Default ${fn.title.firstLower} cannot have the `pub` qualifier")
                }
            }
            RsFunctionRole.FOREIGN -> {
                deny(fn.default, holder, "${fn.title} cannot have the `default` qualifier")
                deny(fn.block, holder, "${fn.title} cannot have a body")
                deny(fn.const, holder, "${fn.title} cannot have the `const` qualifier")
                deny(fn.unsafe, holder, "${fn.title} cannot have the `unsafe` qualifier")
                deny(fn.externAbi, holder, "${fn.title} cannot have an extern ABI")
            }
        }
    }

    private fun checkStructItem(holder: AnnotationHolder, struct: RsStructItem) {
        checkDuplicates(holder, struct)
        if (struct.kind == RsStructKind.UNION && struct.tupleFields != null) {
            deny(struct.tupleFields, holder, "Union cannot be tuple-like")
        }
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
            if (modDecl.module?.cargoWorkspace != null) {
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
        val trait = impl.traitRef?.trait ?: return
        val traitName = trait.name ?: return

        val canImplement = trait.functionList.associateBy { it.name }
        val mustImplement = canImplement.filterValues { it.isAbstract }
        val implemented = impl.functionList.associateBy { it.name }

        val notImplemented = mustImplement.keys - implemented.keys
        if (!notImplemented.isEmpty()) {
            val toImplement = trait.functionList.filter { it.name in notImplemented }
            val implHeaderTextRange = TextRange.create(
                impl.textRange.startOffset,
                impl.typeReference?.textRange?.endOffset ?: impl.textRange.endOffset
            )

            holder.createErrorAnnotation(implHeaderTextRange,
                "Not all trait items implemented, missing: ${notImplemented.namesList} [E0046]"
            ).registerFix(ImplementMethodsFix(impl, toImplement))
        }

        val notMembers = implemented.filterKeys { it !in canImplement }
        for (method in notMembers.values) {
            holder.createErrorAnnotation(method.identifier,
                "Method `${method.name}` is not a member of trait `$traitName` [E0407]")
        }

        implemented
            .map { it.value to canImplement[it.key] }
            .filter { it.second != null }
            .forEach { checkTraitFnImplParams(holder, it.first, it.second!!, traitName) }
    }

    private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
        checkDuplicates(holder, ta)
        val title = "Type `${ta.identifier.text}`"
        when (ta.role) {
            RsTypeAliasRole.FREE -> {
                deny(ta.default, holder, "$title cannot have the `default` qualifier")
                deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                require(ta.typeReference, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
            }
            RsTypeAliasRole.TRAIT_ASSOC_TYPE -> {
                deny(ta.default, holder, "$title cannot have the `default` qualifier")
                deny(ta.vis, holder, "$title cannot have the `pub` qualifier")
                deny(ta.typeParameterList, holder, "$title cannot have generic parameters")
                deny(ta.whereClause, holder, "$title cannot have `where` clause")
            }
            RsTypeAliasRole.IMPL_ASSOC_TYPE -> {
                val impl = ta.parent as? RsImplItem ?: return
                if (impl.`for` == null) {
                    holder.createErrorAnnotation(ta, "Associated types are not allowed in inherent impls [E0202]")
                } else {
                    deny(ta.typeParameterList, holder, "$title cannot have generic parameters")
                    deny(ta.whereClause, holder, "$title cannot have `where` clause")
                    deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                    require(ta.typeReference, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
                }
            }
        }
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
            holder.createErrorAnnotation(o, "Chained comparison operator require parentheses")
        }
    }

    private fun checkValueArgumentList(holder: AnnotationHolder, args: RsValueArgumentList) {
        val parent = args.parent
        val expectedCount = when (parent) {
            is RsCallExpr -> parent.expectedParamsCount()
            is RsMethodCallExpr -> parent.expectedParamsCount()
            else -> null
        } ?: return

        val realCount = args.exprList.size
        if (realCount != expectedCount) {
            holder.createErrorAnnotation(args,
                "This function takes $expectedCount ${pluralise(expectedCount, "parameter", "parameters")}"
                + " but $realCount ${pluralise(realCount, "parameter", "parameters")}"
                + " ${pluralise(realCount, "was", "were")} supplied [E0061]")
        }
    }

    private fun checkRefLikeType(holder: AnnotationHolder, type: RsRefLikeType) {
        if (type.needsLifetime()) {
            require(type.lifetime, holder, "Missing lifetime specifier [E0106]", type.and ?: type)
        }
    }

    private fun require(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el != null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: TextRange.EMPTY_RANGE, message)

    private fun deny(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el == null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

    private inline fun <reified T : RsCompositeElement> denyType(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el !is T) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

    private fun isInTraitImpl(o: RsVis): Boolean {
        val impl = o.parent?.parent
        return impl is RsImplItem && impl.traitRef != null
    }

    private fun RsRefLikeType.needsLifetime(): Boolean {
        val parentTuples = generateSequence(parent as? RsTupleType) { it.parent as? RsTupleType }
        val typeOwner = (parentTuples.lastOrNull() ?: this).parent
        return typeOwner is RsFieldDecl || typeOwner is RsTupleFieldDecl || typeOwner is RsTypeAlias
    }

    private fun isInEnumVariantField(o: RsVis): Boolean {
        val field = o.parent as? RsFieldDecl
            ?: o.parent as? RsTupleFieldDecl
            ?: return false
        return field.parent.parent is RsEnumVariant
    }

    private fun pluralise(count: Int, singular: String, plural: String): String =
        if (count == 1) singular else plural

    private val Array<out PsiElement?>.combinedRange: TextRange?
        get() = if (isEmpty())
            null
        else filterNotNull()
            .map { it.textRange }
            .reduce(TextRange::union)

    private val Collection<String?>.namesList: String
        get() = mapNotNull { "`$it`" }.joinToString(", ")

    private val RsSelfParameter.canonicalDecl: String
        get() = buildString {
            if (isRef) append('&')
            if (isMut) append("mut ")
            append("self")
        }

    private val PsiElement.rightVisibleLeaves: Sequence<PsiElement>
        get() = generateSequence(PsiTreeUtil.nextVisibleLeaf(this), { el -> PsiTreeUtil.nextVisibleLeaf(el) })

    private val String.firstLower: String
        get() = if (isEmpty()) this else this[0].toLowerCase() + substring(1)
}

private fun RsExpr?.isComparisonBinaryExpr(): Boolean {
    val op = this as? RsBinaryExpr ?: return false
    return op.operatorType in RS_COMPARISON_OPERATOR
}

private fun checkDuplicates(holder: AnnotationHolder, element: RsNamedElement, scope: PsiElement = element.parent, recursively: Boolean = false) {
    val duplicates = holder.currentAnnotationSession.duplicatesByNamespace(scope, recursively)
    val ns = element.namespaces.find { element in duplicates[it].orEmpty() }
        ?: return
    val name = element.name!!
    val message = when {
        element is RsFieldDecl -> "Field `$name` is already declared [E0124]"
        element is RsEnumVariant -> "Enum variant `$name` is already declared [E0428]"
        element is RsLifetimeParameter -> "Lifetime name `$name` declared twice in the same scope [E0263]"
        element is RsPatBinding && scope is RsValueParameterList -> "Identifier `$name` is bound more than once in this parameter list [E0415]"
        element is RsTypeParameter -> "The name `$name` is already used for a type parameter in this type parameter list [E0403]"
        scope is RsImplItem -> "Duplicate definitions with name `$name` [E0201]"
        else -> {
            val scopeType = when (scope) {
                is RsBlock -> "block"
                is RsMod, is RsForeignModItem -> "module"
                is RsTraitItem -> "trait"
                else -> "scope"
            }
            "A ${ns.itemName} named `$name` has already been defined in this $scopeType [E0428]"
        }
    }

    holder.createErrorAnnotation(element.navigationElement, message)
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

private fun RsCallExpr.expectedParamsCount(): Int? {
    val path = (expr as? RsPathExpr)?.path ?: return null
    val el = path.reference.resolve()
    if (el is RsDocAndAttributeOwner && el.queryAttributes.hasCfgAttr()) return null
    return when (el) {
        is RsFieldsOwner -> el.tupleFields?.tupleFieldDeclList?.size
        is RsFunction -> {
            if (el.role == RsFunctionRole.IMPL_METHOD && !el.isInherentImpl) return null
            val count = el.valueParameterList?.valueParameterList?.size ?: return null
            // We can call foo.method(1), or Foo::method(&foo, 1), so need to take coloncolon into account
            val s = if (path.coloncolon != null && el.selfParameter != null) 1 else 0
            count + s
        }
        else -> null
    }
}

private fun RsMethodCallExpr.expectedParamsCount(): Int? {
    val fn = reference.resolve() as? RsFunction ?: return null
    if (fn.queryAttributes.hasCfgAttr()) return null
    return if (fn.isInherentImpl) fn.valueParameterList?.valueParameterList?.size else null
}
