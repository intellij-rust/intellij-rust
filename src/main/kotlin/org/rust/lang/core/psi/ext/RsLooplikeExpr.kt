/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsBreakExpr
import org.rust.lang.core.psi.RsContExpr

/**
 * An expression which can contain a [RsLabelReferenceOwner]. It does not however mean that a [RsLabelReferenceOwner]
 * is invalid without it. E.g.: [RsContExpr] (`continue`) can only be valid inside a [RsLooplikeExpr], however a
 * [RsBreakExpr] (`break`) can be valid outside of [RsLooplikeExpr] as well. In general, a [RsLabelReferenceOwner]
 * is expected to be valid inside a [RsLooplikeExpr] if it doesn't have a label.
 */
interface RsLooplikeExpr : RsExpr, RsLabeledExpression, RsOuterAttributeOwner
