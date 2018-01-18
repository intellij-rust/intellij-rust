/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.intentions.SortImplTraitMethodsIntention
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor

class RsSortImplTraitMethodsInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitImplItem(o: RsImplItem) {
            if (!SortImplTraitMethodsIntention.isApplicableTo(o)) return
            holder.registerProblem(
                o,
                "Different impl methods order from the trait",
                IntentionWrapper(SortImplTraitMethodsIntention(), o.containingFile)
            )
        }
    }
}
