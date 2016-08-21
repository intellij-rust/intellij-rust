package org.rust.ide.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.util.cargoProject
import org.rust.ide.actions.RustExpandModuleAction
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.impl.mixin.isPathAttributeRequired
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
        if (modDecl.isPathAttributeRequired && modDecl.pathAttribute == null) {
            val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
            holder.createErrorAnnotation(modDecl, message)
            return
        }

        val containingMod = modDecl.containingMod ?: return
        if (!containingMod.ownsDirectory) {
            // We don't want to show the warning if there is no cargo project
            // associated with the current module. Without it we can't know for
            // sure that a mod is not a directory owner.
            if (modDecl.module?.cargoProject != null) {
                holder.createErrorAnnotation(modDecl, "Cannot declare a new module at this location")
                    .registerFix(AddModuleFile(modDecl, expandModuleFirst = true))
            }
            return
        }

        if (modDecl.reference.resolve() == null) {
            holder.createErrorAnnotation(modDecl, "Unresolved module")
                .registerFix(AddModuleFile(modDecl, expandModuleFirst = false))
        }
    }

    private fun checkImpl(holder: AnnotationHolder, impl: RustImplItemElement) {
        val trait = impl.traitRef?.trait ?: return
        val implHeaderTextRange = TextRange.create(
            impl.textRange.startOffset,
            impl.type?.textRange?.endOffset ?: impl.textRange.endOffset
        )

        val canImplement = trait.traitMethodMemberList.associateBy { it.name }
        val mustImplement = canImplement.filterValues { it.isAbstract }
        val implemented = impl.implMethodMemberList.associateBy { it.name }

        val notImplemented = mustImplement.keys - implemented.keys
        if (!notImplemented.isEmpty()) {
            val toImplement = trait.traitMethodMemberList.filter { it.name in notImplemented }

            holder.createErrorAnnotation(implHeaderTextRange,
                "Not all trait items implemented, missing: `${notImplemented.first()}`"
            ).registerFix(ImplementMethods(impl, toImplement))

        }

        val notMembers = implemented.filterKeys { it !in canImplement }
        for (method in notMembers.values) {
            holder.createErrorAnnotation(method.identifier,
                "Method is not a member of trait `${trait.name}`")
        }
    }
}

private class AddModuleFile(
    modDecl: RustModDeclItemElement,
    private val expandModuleFirst: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(modDecl) {
    override fun getText(): String = "Create module file"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val modDecl = startElement as RustModDeclItemElement
        if (expandModuleFirst) {
            val containingFile = modDecl.containingFile as RustFile
            RustExpandModuleAction.expandModule(containingFile)
        }
        modDecl.getOrCreateModuleFile()?.let { it.navigate(true) }
    }

}

private class ImplementMethods(
    implBody: RustImplItemElement,
    private val methods: List<RustTraitMethodMemberElement>
) : LocalQuickFixAndIntentionActionOnPsiElement(implBody) {
    init {
        check(methods.isNotEmpty())
    }

    override fun getText(): String = "Implement methods"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val impl = (startElement as RustImplItemElement)
        val templateImpl = RustElementFactory.createImplItem(project, methods) ?: return
        val lastMethodOrBrace = impl.implMethodMemberList.lastOrNull() ?: impl.lbrace ?: return
        val firstToAdd = templateImpl.implMethodMemberList.firstOrNull() ?: return
        val lastToAdd = templateImpl.implMethodMemberList.lastOrNull() ?: return
        impl.addRangeAfter(firstToAdd, lastToAdd, lastMethodOrBrace)
    }

}
