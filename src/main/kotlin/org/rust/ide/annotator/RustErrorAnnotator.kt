package org.rust.ide.annotator

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.cargoProject
import org.rust.ide.annotator.fixes.AddModuleFileFix
import org.rust.ide.annotator.fixes.ImplementMethodsFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.trait

class RustErrorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RustElementVisitor() {
            override fun visitConstant(o: RustConstantElement) = checkConstant(holder, o)
            override fun visitImplItem(o: RustImplItemElement) = checkImpl(holder, o)
            override fun visitModDeclItem(o: RustModDeclItemElement) = checkModDecl(holder, o)
            override fun visitPath(o: RustPathElement) = checkPath(holder, o)
            override fun visitTypeAlias(o: RustTypeAliasElement) = checkTypeAlias(holder, o)
            override fun visitVis(o: RustVisElement) = checkVis(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkPath(holder: AnnotationHolder, path: RustPathElement) {
        if (path.asRustPath == null) {
            holder.createErrorAnnotation(path, "Invalid path: self and super are allowed only at the beginning")
        }
    }

    private fun checkVis(holder: AnnotationHolder, vis: RustVisElement) {
        if (vis.parent is RustImplItemElement || vis.parent is RustForeignModItemElement || isInTraitImpl(vis)) {
            holder.createErrorAnnotation(vis, "Unnecessary visibility qualifier [E0449]")
        }
    }

    private fun checkConstant(holder: AnnotationHolder, const: RustConstantElement) {
        val title = if (const.static != null) "Static constant `${const.identifier.text}`" else "Constant `${const.identifier.text}`"
        when (const.role) {
            RustConstantRole.FREE -> {
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                require(const.expr, holder, "$title must have a value", const)
            }
            RustConstantRole.TRAIT_CONSTANT -> {
                deny(const.vis, holder, "$title cannot have the `pub` qualifier")
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                deny(const.static, holder, "Static constants are not allowed in traits")
            }
            RustConstantRole.IMPL_CONSTANT -> {
                deny(const.static, holder, "Static constants are not allowed in impl blocks")
                require(const.expr, holder, "$title must have a value", const)
            }
            RustConstantRole.FOREIGN -> {
                deny(const.default, holder, "$title cannot have the `default` qualifier")
                require(const.static, holder, "Only static constants are allowed in extern blocks", const.const)
                    ?: require(const.mut, holder, "Non mutable static constants are not allowed in extern blocks", const.static, const.identifier)
                deny(const.expr, holder, "Static constants in extern blocks cannot have values", const.eq, const.expr)
            }
        }
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RustModDeclItemElement) {
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

    private fun checkImpl(holder: AnnotationHolder, impl: RustImplItemElement) {
        val trait = impl.traitRef?.trait ?: return
        val implHeaderTextRange = TextRange.create(
            impl.textRange.startOffset,
            impl.type?.textRange?.endOffset ?: impl.textRange.endOffset
        )

        val canImplement = trait.functionList.associateBy { it.name }
        val mustImplement = canImplement.filterValues { it.isAbstract }
        val implemented = impl.functionList.associateBy { it.name }

        val notImplemented = mustImplement.keys - implemented.keys
        if (!notImplemented.isEmpty()) {
            val toImplement = trait.functionList.filter { it.name in notImplemented }

            holder.createErrorAnnotation(implHeaderTextRange,
                "Not all trait items implemented, missing: `${notImplemented.first()}`"
            ).registerFix(ImplementMethodsFix(impl, toImplement))

        }

        val notMembers = implemented.filterKeys { it !in canImplement }
        for (method in notMembers.values) {
            holder.createErrorAnnotation(method.identifier,
                "Method is not a member of trait `${trait.name}`")
        }
    }

    private fun checkTypeAlias(holder: AnnotationHolder, ta: RustTypeAliasElement) {
        val title = "Type `${ta.identifier.text}`"
        when (ta.role) {
            RustTypeAliasRole.FREE -> {
                deny(ta.default, holder, "$title cannot have the `default` qualifier")
                deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                require(ta.type, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
            }
            RustTypeAliasRole.TRAIT_ASSOC_TYPE -> {
                deny(ta.default, holder, "$title cannot have the `default` qualifier")
                deny(ta.vis, holder, "$title cannot have the `pub` qualifier")
                deny(ta.genericParams, holder, "$title cannot have generic parameters")
                deny(ta.whereClause, holder, "$title cannot have `where` clause")
            }
            RustTypeAliasRole.IMPL_ASSOC_TYPE -> {
                val impl = ta.parent as? RustImplItemElement ?: return
                if (impl.`for` == null) {
                    holder.createErrorAnnotation(ta, "Associated types are not allowed in inherent impls [E0202]")
                } else {
                    deny(ta.genericParams, holder, "$title cannot have generic parameters")
                    deny(ta.whereClause, holder, "$title cannot have `where` clause")
                    deny(ta.typeParamBounds, holder, "$title cannot have type parameter bounds")
                    require(ta.type, holder, "Aliased type must be provided for type `${ta.identifier.text}`", ta)
                }
            }
        }
    }

    private fun require(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el != null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: TextRange.EMPTY_RANGE, message)

    private fun deny(el: PsiElement?, holder: AnnotationHolder, message: String, vararg highlightElements: PsiElement?): Annotation? =
        if (el == null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

    private val Array<out PsiElement?>.combinedRange: TextRange?
        get() = if (isEmpty())
            null
        else filterNotNull()
            .map { it.textRange }
            .reduce(TextRange::union)

    private fun isInTraitImpl(o: RustVisElement): Boolean {
        val impl = o.parent?.parent
        return impl is RustImplItemElement && impl.traitRef != null
    }
}
