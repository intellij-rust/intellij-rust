/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.lang.core.psi.RsExpr

data class DataFlowAnalysisResult(val runnerResult: DfaRunnerResult, val result: DfaResult)

data class DfaResult(
    val trueSet: Set<RsExpr>,
    val falseSet: Set<RsExpr>,
    val overflowExpressions: Set<RsExpr>,
    val exception: DfaException?,
    val resultState: DfaMemoryState
)

enum class DfaRunnerResult {
    /**
     * Successful completion
     */
    OK,
    /**
     * Method is too complex for analysis
     */
    TOO_COMPLEX,
    /**
     * Cannot analyze (probably method in severely incomplete)
     */
    NOT_APPLICABLE,
    /**
     * Aborted due to some internal error like corrupted stack
     */
    ABORTED
}
