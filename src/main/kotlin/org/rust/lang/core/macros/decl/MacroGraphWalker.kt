/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.macros.decl.MGNodeData.*
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import java.util.*
import kotlin.math.min

/**
 * Walks the [graph] along with macro [callBody] and determines possible [FragmentKind]s for the given [caretOffset]
 */
class MacroGraphWalker(
    project: Project,
    private val graph: MacroGraph,
    private val callBody: String,
    private val caretOffset: Int
) {
    private enum class Status { Active, Dead, Finished }

    data class FragmentDescriptor(
        val fragmentText: String,
        val caretOffsetInFragment: Int,
        val kind: FragmentKind
    )

    data class State(
        val position: MacroGraphNode,
        val marker: PsiBuilder.Marker?,
        val descriptor: FragmentDescriptor?
    )

    private fun rollbackToState(state: State) {
        position = state.position
        state.marker?.rollbackTo()
        descriptor = state.descriptor
    }

    private val builder = project.createAdaptedRustPsiBuilder(callBody).also { it.eof() } // skip whitespace
    private val processStack: Deque<State> = ArrayDeque()
    private var position: MacroGraphNode = graph.getNode(0)
    private var status: Status = Status.Active

    private var descriptor: FragmentDescriptor? = null
    private val result: MutableList<FragmentDescriptor> = mutableListOf()

    fun run(): List<FragmentDescriptor> {
        processStack.push(State(position, builder.mark(), descriptor))

        while (processStack.isNotEmpty()) {
            status = Status.Active
            val state = processStack.pop()
            rollbackToState(state)

            processMatcher()

            when (status) {
                Status.Active -> {
                    val nextNodes = graph.outgoingEdges(position).map { it.target }.toList()

                    if (nextNodes.size == 1) {
                        // `nodeState` will be processed in the next iteration, so we don't have to rollback lexer
                        val nodeState = State(nextNodes.single(), null, descriptor)
                        processStack.push(nodeState)
                    } else {
                        for (node in nextNodes) {
                            val nodeState = State(node, builder.mark(), descriptor)
                            processStack.push(nodeState)
                        }
                    }
                }
                Status.Dead -> {
                    descriptor = null
                }
                Status.Finished -> {
                    descriptor?.let { result.add(it) }
                }
            }
        }

        return result
    }

    private fun processMatcher() {
        when (val matcher = position.data) {
            is Literal -> {
                if (!builder.isSameToken(matcher.value)) {
                    status = Status.Dead
                }
            }
            is Fragment -> {
                val fragmentStart = builder.currentOffset
                if (matcher.kind.parse(builder)) {
                    if (descriptor == null) {
                        val textRange = TextRange(fragmentStart, builder.currentOffset + 1)
                        if (textRange.contains(caretOffset) || builder.eof()) {
                            val fragmentEnd = min(builder.currentOffset, callBody.length)
                            val fragmentText = callBody.substring(fragmentStart, fragmentEnd)
                            val caretOffsetInFragment = caretOffset - fragmentStart
                            descriptor = FragmentDescriptor(fragmentText, caretOffsetInFragment, matcher.kind)
                        }
                    }
                } else {
                    status = Status.Dead
                }
            }
            End -> {
                status = if (builder.eof()) {
                    Status.Finished
                } else {
                    Status.Dead
                }
            }
        }
        if (status == Status.Active && builder.eof()) {
            status = Status.Finished
        }
    }
}
