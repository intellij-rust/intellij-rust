/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.types.lifetimeElidable

class RsWrongLifetimeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return

                val paramsDecl = type.path?.reference?.resolve() as? RsGenericDeclaration ?: return
                val expectedLifetimes = paramsDecl.typeParameterList?.lifetimeParameterList?.size ?: 0
                val actualLifetimes = type.path?.typeArgumentList?.lifetimeList?.size ?: 0
                if (expectedLifetimes == actualLifetimes) return
                if (actualLifetimes == 0 && !type.lifetimeElidable) {
                    holder.registerProblem(type, "Missing lifetime specifier [E0106]")
                } else if (actualLifetimes > 0) {
                    holder.registerProblem(type, "Wrong number of lifetime parameters: expected $expectedLifetimes, found $actualLifetimes [E0107]")
                }
            }

            override fun visitRefLikeType(type: RsRefLikeType) {
                if (type.mul == null && !type.lifetimeElidable && type.lifetime == null) {
                    holder.registerProblem(type.and ?: type, "Missing lifetime specifier [E0106]")
                }
            }
        }

}
