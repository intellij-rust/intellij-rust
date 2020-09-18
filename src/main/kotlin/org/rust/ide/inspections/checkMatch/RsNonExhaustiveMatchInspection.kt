/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.utils.checkMatch.checkExhaustive
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsNonExhaustiveMatchInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "Non-exhaustive match"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(matchExpr: RsMatchExpr) {
            val patterns = matchExpr.checkExhaustive() ?: return
            RsDiagnostic.NonExhaustiveMatch(matchExpr, patterns).addToHolder(holder)
        }
    }
}
