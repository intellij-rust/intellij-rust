/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsExpr

interface RsMethodOrField : RsReferenceElement

val RsMethodOrField.parentDotExpr: RsDotExpr get() = parent as RsDotExpr
val RsMethodOrField.receiver: RsExpr get() = parentDotExpr.expr
