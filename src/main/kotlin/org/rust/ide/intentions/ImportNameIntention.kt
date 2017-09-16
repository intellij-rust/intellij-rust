/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.ty.TyPrimitive

class ImportNameIntention : RsElementBaseIntentionAction<RsPath>(), HighPriorityAction {
    override fun getText() = "Import name" // TODO
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsPath? {
        val path = element.parentOfType<RsPath>() ?: return null
        if (TyPrimitive.fromPath(path) != null || path.reference.resolve() != null) return null

        val parent = path.path
        val parentRes = parent?.reference?.resolve()
        if (parent == null || parentRes is RsMod || parentRes is RsEnumItem) {
            return path
        }

        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsPath) {
        val path = getBasePath(ctx)
        val candidates = RsNamedElementIndex.findElementsByName(project, path.referenceName)
        if (candidates.isEmpty()) return

        // Here we should do the most complicated things
        // First, if there are multiple candidates, we should display a choose dialog (see AddImportAction:97)
        // Then we should calculate path to the found item. It is the most complicated step because of re-exports.
        // We can easily find out an absolute path in a crate module structure, but this will make little sense
        // if the name is re-exported. For example:
        // ```rust
        // mod a {
        //     mod b1 {
        //         pub struct S;
        //     }
        //     pub mod b2 {
        //         pub use super::b1::S;
        //     }
        // }
        // use a::b2::S;
        // ```
        // Or even it can be re-exported with another name `pub use super::b1::S as S1;`
        //
        // I don't completely know what to do with this, may be create another index for re-exports,
        // but we can start with the trivial case where actual path == path in module structure
        // (check it by visibility)
        //
        // Finally, we should place `use $path;` to the current file

        TODO("not implemented")
    }
}

private tailrec fun getBasePath(path: RsPath): RsPath {
    val qualifier = path.path
    return if (qualifier == null) path else getBasePath(qualifier)
}
