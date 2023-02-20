/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES_N_VALUES_N_MACROS

class RsUnnecessaryQualificationsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedQualifications

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitPath(path: RsPath) {
            val shouldCheckPath = path.parentOfType<RsUseItem>() == null
                && path.parentOfType<RsVisRestriction>() == null
                && path.rootPath() == path
                && path.canBeShortened()
            if (shouldCheckPath) {
                val target = getUnnecessarilyQualifiedPath(path)
                if (target != null) {
                    val pathRestStart = target.referenceNameElement!!.startOffset
                    val unnecessaryLength = pathRestStart - path.startOffset
                    val range = TextRange(0, unnecessaryLength)

                    val fix = SubstituteTextFix.delete(
                        "Remove unnecessary path prefix",
                        path.containingFile,
                        TextRange(path.startOffset, pathRestStart)
                    )
                    holder.registerLintProblem(path, "Unnecessary qualification", range, RsLintHighlightingType.UNUSED_SYMBOL, listOf(fix))
                }
            }
            super.visitPath(path)
        }
    }

    /**
     * Consider a path like `a::b::c::d`.
     * We will try to walk it from the "root" (the rightmost sub-path).
     * First we try `d`, then `c::d`, then `b::c::d`, etc.
     * Once we find a path that can be resolved and which is shorter than the original path, we will return it.
     */
    private fun getUnnecessarilyQualifiedPath(path: RsPath): RsPath? {
        if (path.resolveStatus == PathResolveStatus.UNRESOLVED) return null

        val target = path.reference?.resolve() ?: return null

        // From `a::b::c::d` generates paths `a::b::c::d`, `a::b::c`, `a::b` and `a`.
        val subPaths = generateSequence(path) { it.qualifier }.toList()

        // If any part of the path has type qualifiers/arguments, we don't want to erase parts of that path to the
        // right of the type, because the qualifiers/arguments can be important for type inference.
        val lastSubPathWithType = subPaths.indexOfLast { it.typeQual != null || it.typeArgumentList != null }
            .takeIf { it != -1 } ?: 0

        val rootPathText = subPaths.getOrNull(0)?.referenceName ?: return null

        //  From `a::b::c::d` generates strings `d`, `c::d`, `b::c::d`, `a::b::c::d`.
        val pathTexts = subPaths.drop(1).runningFold(rootPathText) { accumulator, subPath ->
            "${subPath.referenceName}::$accumulator"
        }

        val basePath = path.basePath()
        for ((subPath, subPathText) in subPaths.zip(pathTexts).drop(lastSubPathWithType)) {
            val fragment = RsPathCodeFragment(
                path.project, subPathText, false, path, RustParserUtil.PathParsingMode.TYPE,
                TYPES_N_VALUES_N_MACROS
            )

            // If the subpath is resolvable, we want to ignore it if it is the last fragment ("base") of the original path.
            // However, leading `::` has to be handled in a special way.
            // If `a::b::c::d` resolves to the same item as `::a::b::c::d`, we don't want to ignore `a`.
            // To recognize this, we check if the current path corresponds to the base path, and if the base path
            // doesn't have a leading `::`. In that case, the paths are the same and there's nothing to shorten.
            // On the other hand, `subPathText` will never have a leading `::`, so if the base path has `::` and `a`
            // resolves to the same thing as `::a`, we can still shorten the path.
            val sameAsBase = subPath == basePath && !basePath.hasColonColon
            if (fragment.path?.reference?.resolve() == target && !sameAsBase) {
                return subPath
            }
        }
        return null
    }
}

private fun RsPath.canBeShortened(): Boolean {
    return path != null || coloncolon != null
}
