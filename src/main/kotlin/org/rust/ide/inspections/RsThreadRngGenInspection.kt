/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.findInScope
import org.rust.lang.core.psi.ext.isIntentionPreviewElement
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.VALUES
import org.rust.lang.core.resolve.knownItems

class RsThreadRngGenInspection : RsLocalInspectionTool() {
    override fun buildVisitor(
        holder: RsProblemsHolder,
        isOnTheFly: Boolean,
    ): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitDotExpr(o: RsDotExpr) {
            val left = o.expr as? RsCallExpr
            val functionPath = left?.expr as? RsPathExpr
            val function = functionPath?.path?.reference?.resolve()
            if (function == null || function != o.knownItems.threadRng) return
            val method = o.methodCall?.reference?.resolve() as? RsFunction
            if (method == null || method != o.knownItems.gen) return
            val typeArgument = o.methodCall?.typeArgumentList?.text.orEmpty()
            val randomResolvedIncorrectly = run {
                val resolved = o.findInScope("random", VALUES)
                if (resolved == null) false else resolved != o.knownItems.random
            }
            holder.registerProblem(
                o,
                RsBundle.getMessage("inspection.message.can.be.replaced.with.random", typeArgument),
                ReplaceWithRandomCall(o, typeArgument, randomResolvedIncorrectly),
            )
        }
    }

    private class ReplaceWithRandomCall(
        element: RsDotExpr,
        private val typeArgument: String,
        private val needsQualifiedName: Boolean,
    ) : RsQuickFixBase<RsDotExpr>(element) {
        @NlsSafe
        private val random = if (needsQualifiedName) "rand::random" else "random"
        @Nls
        private val _text = RsBundle.message("intention.name.replace.with2", "$random$typeArgument()")

        override fun getFamilyName() = _text
        override fun getText() = _text
        override fun invoke(project: Project, editor: Editor?, element: RsDotExpr) {
            val psiFactory = RsPsiFactory(project)
            val randomCall = psiFactory.createExpression("$random$typeArgument()")

            val newElement = element.replace(randomCall) as RsCallExpr

            if (newElement.isIntentionPreviewElement) return

            if (!needsQualifiedName && newElement.expr.let { it as RsPathExpr }.path.reference?.resolve() == null) {
                RsImportHelper.importElement(newElement, newElement.knownItems.random!!)
            }
        }
    }

    companion object {
        private val KnownItems.threadRng get() = findItem<RsFunction>("rand::rngs::thread::thread_rng", false)
        private val KnownItems.gen get() = findItem<RsFunction>("rand::rng::Rng::gen", false)
        private val KnownItems.random get() = findItem<RsFunction>("rand::random", false)
    }
}
