/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.fixes.RemovePathPrefixFix
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathCodeFragment
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES_N_VALUES_N_MACROS

class RsUnnecessaryQualificationsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedQualifications

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsVisitor() {
        override fun visitPath(path: RsPath) {
            val root = path.rootPath()
            val useItem = path.parentOfType<RsUseItem>()

            if (useItem == null && root == path && root.canBeShortened()) {
                val target = getUnnecessarilyQualifiedPath(path)
                if (target != null) {
                    val unnecessaryPart = target.fullQualifier()
                    val range = TextRange(0, unnecessaryPart.length)

                    val fix = RemovePathPrefixFix(path.containingFile,
                        TextRange(root.startOffset, root.startOffset + unnecessaryPart.length)
                    )
                    holder.registerLintProblem(root, "Unnecessary qualification", range, fix)
                }
            }
            super.visitPath(path)
        }
    }

    private fun getUnnecessarilyQualifiedPath(path: RsPath): RsPath? {
        if (path.resolveStatus == PathResolveStatus.UNRESOLVED) return null

        val target = path.reference?.resolve() ?: return null

        var pathText = path.referenceName ?: return null
        var currentPath = path
        val basePath = path.basePath()

        while (true) {
            val fragment = RsPathCodeFragment(
                path.project, pathText, false, path, RustParserUtil.PathParsingMode.TYPE,
                TYPES_N_VALUES_N_MACROS
            )
            if (fragment.path?.reference?.resolve() == target) {
                return currentPath
            }
            currentPath = path.qualifier ?: break
            if (currentPath == basePath) {
                break
            }
            val currentName = currentPath.referenceName ?: break
            pathText = "$currentName::$pathText"
        }

        return null
    }
}

private fun RsPath.canBeShortened(): Boolean {
    return path != null || coloncolon != null
}

/**
 * Returns the full qualifier of a path.
 * `foo::bar::baz` -> `foo::bar::`
 */
private fun RsPath.fullQualifier(): String {
    val qualPath = qualifier ?: return coloncolon?.text ?: ""
    return "${qualPath.fullQualifier()}${qualPath.referenceName}::"
}
