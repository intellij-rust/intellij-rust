package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.cargoProject
import org.rust.ide.annotator.fixes.AddModuleFileFix
import org.rust.ide.annotator.fixes.ImplementMethodsFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import java.util.*

class RsErrorAnnotator : Annotator, HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile): Boolean = file is RsFile

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RsVisitor() {
            override fun visitBlock(o: RsBlock) = checkBlock(holder, o)
            override fun visitConstant(o: RsConstant) = checkConstant(holder, o)
            override fun visitStructItem(o: RsStructItem) = checkStructItem(holder, o)
            override fun visitEnumBody(o: RsEnumBody) = checkEnumBody(holder, o)
            override fun visitForeignModItem(o: RsForeignModItem) = checkForeignModItem(holder, o)
            override fun visitFunction(o: RsFunction) = checkFunction(holder, o)
            override fun visitImplItem(o: RsImplItem) = checkImpl(holder, o)
            override fun visitModDeclItem(o: RsModDeclItem) = checkModDecl(holder, o)
            override fun visitModItem(o: RsModItem) = checkModItem(holder, o)
            override fun visitPath(o: RsPath) = checkPath(holder, o)
            override fun visitBlockFields(o: RsBlockFields) = checkBlockFields(holder, o)
            override fun visitTraitItem(o: RsTraitItem) = checkTraitItem(holder, o)
            override fun visitTypeAlias(o: RsTypeAlias) = checkTypeAlias(holder, o)
            override fun visitTypeParameterList(o: RsTypeParameterList) = checkTypeParameterList(holder, o)
            override fun visitValueParameterList(o: RsValueParameterList) = checkValueParameterList(holder, o)
            override fun visitValueParameter(o: RsValueParameter) = checkValueParameter(holder, o)
            override fun visitVis(o: RsVis) = checkVis(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkBlock(holder: AnnotationHolder, block: RsBlock) =
        findDuplicates(holder, block, { ns, name ->
            "A ${ns.itemName} named `$name` has already been defined in this block [E0428]"
        })

    private fun checkBlockFields(holder: AnnotationHolder, block: RsBlockFields) =
        findDuplicates(holder, block, { ns, name ->
            "Field `$name` is already declared [E0124]"
        })

    private fun checkPath(holder: AnnotationHolder, path: RsPath) {
        val child = path.path
        if ((child == null || child.asRustPath != null) && path.asRustPath == null) {
            holder.createErrorAnnotation(path, "Invalid path: self and super are allowed only at the beginning")
        }
    }

    private fun checkVis(holder: AnnotationHolder, vis: RsVis) {
        if (vis.parent is RsImplItem || vis.parent is RsForeignModItem || isInTraitImpl(vis) || isInEnumVariantField(vis) ) {
            holder.createErrorAnnotation(vis, "Unnecessary visibility qualifier [E0449]")
        }
    }

    private fun checkEnumBody(holder: AnnotationHolder, enum: RsEnumBody) =
        findDuplicates(holder, enum, { ns, name ->
            "A ${ns.itemName} named `$name` has already been defined in this enum [E0428]"
        })

    private fun checkConstant(holder: AnnotationHolder, const: RsConstant) {
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
                    ?: require(const.mut, holder, "Non mutable static constants are not allowed in extern blocks", const.static, const.identifier)
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
        if (struct.kind == RsStructKind.UNION && struct.tupleFields != null) {
            deny(struct.tupleFields, holder, "Union cannot be tuple-like")
        }
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RsModDeclItem) {
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
            if (modDecl.module?.cargoProject != null) {
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

    private fun checkModItem(holder: AnnotationHolder, modItem: RsModItem) =
        findDuplicates(holder, modItem, { ns, name ->
            "A ${ns.itemName} named `$name` has already been defined in this module [E0428]"
        })

    private fun checkForeignModItem(holder: AnnotationHolder, mod: RsForeignModItem) =
        findDuplicates(holder, mod, { ns, name ->
            "A ${ns.itemName} named `$name` has already been defined in this module [E0428]"
        })

    private fun checkTypeParameterList(holder: AnnotationHolder, params: RsTypeParameterList) =
        findDuplicates(holder, params, { ns, name ->
            if (ns == Namespace.Lifetimes)
                "Lifetime name `$name` declared twice in the same scope [E0263]"
            else
                "The name `$name` is already used for a type parameter in this type parameter list [E0403]"
        })

    private fun checkImpl(holder: AnnotationHolder, impl: RsImplItem) {
        findDuplicates(holder, impl, { ns, name ->
            "Duplicate definitions with name `$name` [E0201]"
        })

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
                impl.type?.textRange?.endOffset ?: impl.textRange.endOffset
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

    private fun checkTraitItem(holder: AnnotationHolder, trait: RsTraitItem) =
        findDuplicates(holder, trait, { ns, name ->
            "A ${ns.itemName} named `$name` has already been defined in this trait [E0428]"
        })

    private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
        val title = "Type `${ta.identifier.text}`"
        when (ta.role) {
            RsTypeAliasRole.FREE -> {
                deny(ta.default, holder, "$title cannot have the `default` qualifier")
                deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                require(ta.type, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
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
                    require(ta.type, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
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

    private fun require(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el != null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: TextRange.EMPTY_RANGE, message)

    private fun deny(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el == null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

    private inline fun <reified T: RsCompositeElement>denyType(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el !is T) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

    private fun isInTraitImpl(o: RsVis): Boolean {
        val impl = o.parent?.parent
        return impl is RsImplItem && impl.traitRef != null
    }

    private fun isInEnumVariantField(o: RsVis): Boolean {
        val field = o.parent as? RsFieldDecl
                    ?: o.parent as? RsTupleFieldDecl
                    ?: return false
        return field.parent.parent is RsEnumVariant
    }

    private fun findDuplicates(holder: AnnotationHolder, owner: RsCompositeElement, messageGenerator: (ns: Namespace, name: String) -> String) {
        val marked = HashSet<RsNamedElement>()
        owner.children.asSequence()
            .filterIsInstance<RsNamedElement>()
            .filter { it.name != null }
            .flatMap { it.namespaced }
            .groupBy { it.first }       // Group by namespace
            .forEach { entry ->
                val namespace = entry.key
                val items = entry.value
                items.asSequence()
                    .map { it.second }
                    .groupBy { it.name }
                    .map { it.value }
                    .filter { it.size > 1 && it.any { !it.isCfgDependent } }
                    .flatMap { it.drop(1) }
                    .filterNot { marked.contains(it) }
                    .forEach {
                        holder.createErrorAnnotation(it.navigationElement, messageGenerator(namespace, it.name!!))
                        marked.add(it)
                    }
            }
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

    private val RsCompositeElement.isCfgDependent: Boolean
        get() = this is RsDocAndAttributeOwner && queryAttributes.hasAttribute("cfg")

    private val RsNamedElement.namespaced: Sequence<Pair<Namespace, RsNamedElement>>
        get() = namespaces.asSequence().map { Pair(it, this) }

    private val PsiElement.rightVisibleLeaves: Sequence<PsiElement>
        get() = generateSequence(PsiTreeUtil.nextVisibleLeaf(this), { el -> PsiTreeUtil.nextVisibleLeaf(el) })

    private val String.firstLower: String
        get() = if (isEmpty()) this else this[0].toLowerCase() + substring(1)
}
