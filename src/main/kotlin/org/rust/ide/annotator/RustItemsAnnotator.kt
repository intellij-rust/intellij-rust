package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.cargoProject
import org.rust.ide.annotator.fixes.AddModuleFileFix
import org.rust.ide.annotator.fixes.ImplementMethodsFix
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.mixin.isAbstract
import org.rust.lang.core.psi.impl.mixin.isLocal
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.trait

class RustItemsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RustElementVisitor() {
            override fun visitModDeclItem(o: RustModDeclItemElement) = checkModDecl(holder, o)
            override fun visitImplItem(o: RustImplItemElement) = checkImpl(holder, o)
        }

        element.accept(visitor)
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
}
