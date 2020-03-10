/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.infer.RsInferenceContext

class PathExprResolver(resolver: (RsPathExpr) -> RsElement?): (RsPathExpr) -> RsElement? by resolver {
    companion object {
        val default: PathExprResolver = PathExprResolver { it.path.reference.resolve() }

        fun fromContext(ctx: RsInferenceContext): PathExprResolver =
            PathExprResolver { ctx.getResolvedPath(it).singleOrNull()?.element }
    }
}
