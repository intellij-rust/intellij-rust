/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.fieldTypes
import org.rust.lang.core.psi.ext.findPreviewCopyIfNeeded
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.isMovesByDefault
import org.rust.lang.core.types.type

class DeriveCopyFix(element: RsPathExpr) : RsQuickFixBase<RsPathExpr>(element) {
    override fun getFamilyName(): String = name
    override fun getText(): String = RsBundle.message("intention.name.derive.copy.trait")

    override fun invoke(project: Project, editor: Editor?, element: RsPathExpr) {
        val type = element.type as? TyAdt ?: return
        val item = type.item.findPreviewCopyIfNeeded()

        val implLookup = ImplLookup.relativeTo(item)
        val isCloneImplemented = implLookup.isClone(type).isTrue

        val traits = if (isCloneImplemented) "Copy" else "Clone, Copy"
        DeriveTraitsFix.invoke(item, traits)
    }

    companion object {
        fun createIfCompatible(element: RsElement): DeriveCopyFix? {
            val pathExpr = element as? RsPathExpr ?: return null
            val item = (pathExpr.type as? TyAdt)?.item ?: return null
            if (item.containingCrate.origin != PackageOrigin.WORKSPACE) return null

            val implLookup = ImplLookup.relativeTo(element)

            when (item) {
                is RsStructItem -> {
                    val fieldTypes = item.fieldTypes
                    if (fieldTypes.any { it.isMovesByDefault(implLookup) }) return null
                }
                is RsEnumItem -> {
                    for (variant in item.variants) {
                        if (variant.fieldTypes.any { it.isMovesByDefault(implLookup) }) return null
                    }
                }
            }

            return DeriveCopyFix(pathExpr)
        }
    }
}
