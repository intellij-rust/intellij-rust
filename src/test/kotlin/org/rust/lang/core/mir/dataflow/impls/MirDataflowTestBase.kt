/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.mir.MirBuilder
import org.rust.lang.core.mir.MirPrettyPrinter
import org.rust.lang.core.mir.dataflow.framework.Analysis
import org.rust.lang.core.mir.dataflow.framework.Results
import org.rust.lang.core.mir.dataflow.framework.ResultsVisitor
import org.rust.lang.core.mir.get
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.stdext.singleOrFilter

abstract class MirDataflowTestBase<Domain: Any> : RsTestBase() {
    abstract fun createAnalysis(body: MirBody): Analysis<Domain>
    abstract fun formatState(state: Domain): String
    abstract fun formatStateDiff(oldState: Domain, newState: Domain): String?

    fun doTest(
        @Language("Rust") code: String,
        mir: String,
    ) {
        InlineFile(code)
        val mirBody = MirBuilder.build(myFixture.file as RsFile)
            .singleOrFilter { it.sourceElement.let { fn -> fn is RsFunction && fn.name == "main" }  }
            .single()
        val analysisResult = createAnalysis(mirBody)
            .intoEngine(mirBody)
            .iterateToFixPoint()
        val builtMirStr = MirPrettyPrinter(
            mir = mirBody,
            commentSupplier = DataflowStateCommentSupplier(analysisResult)
        ).print()
        assertEquals(mir.trimIndent(), builtMirStr)
    }

    private inner class DataflowStateCommentSupplier(
        private val results: Results<Domain>,
    ) : MirPrettyPrinter.CommentSupplier {
        private lateinit var stateDiff: Map<Any, String?>
        private lateinit var blockExitState: Domain

        override fun blockStartComment(block: MirBasicBlock): String {
            val blockOnEntryState = results.blockStates[block]
            val collector = StateDiffCollector(results.analysis, blockOnEntryState)
            results.direction.visitResultsInBlock(block, results, collector)
            stateDiff = collector.stateDiff
            blockExitState = collector.prevState
            return formatState(blockOnEntryState)
        }

        override fun blockEndComment(block: MirBasicBlock): String {
            return formatState(blockExitState)
        }

        override fun statementComment(stmt: MirStatement): String? {
            return stateDiff[stmt]
        }

        override fun terminatorComment(terminator: MirTerminator<*>): String? {
            return stateDiff[terminator]
        }
    }

    private inner class StateDiffCollector(
        private val analysis: Analysis<Domain>,
        var prevState: Domain,
    ) : ResultsVisitor<Domain> {
        val stateDiff: MutableMap<Any, String?> = mutableMapOf()

        override fun visitStatementAfterPrimaryEffect(state: Domain, statement: MirStatement, location: MirLocation) {
            stateDiff[statement] = formatStateDiff(prevState, state)
            prevState = analysis.copyState(state)
        }

        override fun visitTerminatorAfterPrimaryEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
            stateDiff[terminator] = formatStateDiff(prevState, state)
            prevState = analysis.copyState(state)
        }
    }
}
