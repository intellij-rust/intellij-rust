/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportInfo
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.namespaces

class AddImportIntention : RsElementBaseIntentionAction<AddImportIntention.Context>() {
    override fun getFamilyName() = "Add import"

    class Context(val path: RsPath, val needsImport: Boolean)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // ignore paths in use items
        if (element.parentOfType<RsUseItem>() != null) return null

        val path = element.parentOfType<RsPath>() ?: return null
        val importablePath = path.findImportablePath() ?: return null
        if (importablePath.kind != PathKind.IDENTIFIER ||
            importablePath.reference == null ||
            importablePath.isUnresolved) return null

        // ignore paths that cannot be shortened
        if (importablePath.path == null) return null

        val target = importablePath.reference?.resolve() as? RsQualifiedNamedElement ?: return null
        val namespace = target.namespaces

        // check if the name is already in scope in the same namespace
        val existingItem = importablePath.findInScope(importablePath.referenceName, namespace)
        val sameReference = target == existingItem

        // if name exists, but is a different type, exit
        if (existingItem != null && !sameReference) {
            return null
        }

        text = "Add import for `${importablePath.text}`"
        return Context(importablePath, existingItem == null)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val path = ctx.path

        if (ctx.needsImport) {
            val targetItem = path.reference?.resolve() as? RsQualifiedNamedElement ?: return
            val usePath = path.generateUsePath()
            val candidate = ImportCandidate(
                QualifiedNamedItem.ExplicitItem(targetItem),
                ImportInfo.LocalImportInfo(usePath)
            )
            candidate.import(ctx.path)
        }

        val target = ctx.path.reference?.resolve() as? RsQualifiedNamedElement ?: return
        ReferencesSearch.search(target, LocalSearchScope(path.containingMod)).forEach {
            if (it.element.parentOfType<RsUseItem>() != null) return@forEach
            val pathReference = it.element as? RsPath ?: return@forEach
            pathReference.shorten(target)
        }
    }
}

/**
 * Shortens the path so that it resolves to `target` without any additional qualification.
 * a::b::Foo.shorten(Foo) -> Foo
 * a::b::Foo.shorten(b) -> b::Foo
 */
private fun RsPath.shorten(target: RsQualifiedNamedElement) {
    val resolved = reference?.resolve() as? RsQualifiedNamedElement
    if (resolved != null) {
        if (resolved == target) {
            path?.delete()
            coloncolon?.delete()
            return
        }
    }

    path?.shorten(target)
}

/**
 * Finds the first part of the path that is possibly importable
 * a::b::Struct::function -> a::b::Struct
 */
private fun RsPath.findImportablePath(): RsPath? {
    val target = reference?.resolve() as? RsQualifiedNamedElement ?: return path?.findImportablePath()

    val canBeImported = when (target) {
        is RsStructOrEnumItemElement,
        is RsMod,
        is RsTraitAlias,
        is RsTraitItem,
        is RsTypeAlias,
        is RsEnumVariant,
        is RsMacro -> true
        is RsAbstractable -> !target.owner.isImplOrTrait
        else -> false
    }
    if (canBeImported) {
        return this
    }
    return path?.findImportablePath()
}

private fun RsPath.generateUsePath(): String = generateSequence(this) { it.path }
    .map { it.referenceName }
    .toList()
    .asReversed()
    .joinToString("::")
