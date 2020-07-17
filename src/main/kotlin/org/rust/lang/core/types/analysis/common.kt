/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.analysis

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.borrowck.*
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.infer.Categorization
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.MemoryCategorizationContext
import org.rust.lang.core.types.infer.RsInferenceResult

enum class DeclarationKind { Parameter, Variable }

data class ProblematicDeclaration(val binding: RsPatBinding, val kind: DeclarationKind)

abstract class UsageAnalysisData<DataFlow>(
    val usages: MutableSet<Usage> = linkedSetOf(),
    val declarations: MutableSet<Declaration> = linkedSetOf(),
    val paths: MutableList<UsagePath> = mutableListOf(),
    protected val pathToIndex: MutableMap<UsagePath, Int> = mutableMapOf()
) {
    private fun addUsagePath(usagePath: UsagePath) {
        if (!pathToIndex.containsKey(usagePath)) {
            val index = paths.size
            paths.add(usagePath)
            pathToIndex[usagePath] = index
        }
    }

    fun eachBasePath(usagePath: UsagePath, predicate: (UsagePath) -> Boolean): Boolean {
        var path = usagePath
        while (true) {
            if (!predicate(path)) return false
            when (path) {
                is UsagePath.Base -> return true
                is UsagePath.Extend -> path = path.parent
            }
        }
    }

    abstract fun addGenKills(dataflow: DataFlow)

    fun addUsage(element: RsElement, cmt: Cmt) {
        val usagePath = UsagePath.computeFor(cmt) ?: return
        if (!pathToIndex.containsKey(usagePath)) return
        usages.add(Usage(usagePath, element))
    }

    fun addDeclaration(element: RsPatBinding) {
        val usagePath = UsagePath.Base(element)
        addUsagePath(usagePath)
        declarations.add(Declaration(usagePath))
    }
}

sealed class UsagePath {
    abstract val declaration: RsPatBinding

    data class Base(override val declaration: RsPatBinding) : UsagePath() {
        override fun toString(): String = declaration.text
    }

    data class Extend(val parent: UsagePath) : UsagePath() {
        override val declaration: RsPatBinding = parent.declaration
        override fun toString(): String = "Extend($parent)"
    }

    private val base: Base
        get() = when (this) {
            is Base -> this
            is Extend -> parent.base
        }

    val declarationKind: DeclarationKind
        get() = if (base.declaration.ancestorOrSelf<RsValueParameter>() != null) {
            DeclarationKind.Parameter
        } else {
            DeclarationKind.Variable
        }

    companion object {
        fun computeFor(cmt: Cmt): UsagePath? {
            return when (val category = cmt.category) {
                is Categorization.Rvalue -> {
                    val declaration = (cmt.element as? RsExpr)?.declaration as? RsPatBinding ?: return null
                    Base(declaration)
                }

                is Categorization.Local -> {
                    val declaration = category.declaration as? RsPatBinding ?: return null
                    Base(declaration)
                }

                is Categorization.Deref -> {
                    val baseCmt = category.unwrapDerefs()
                    computeFor(baseCmt)
                }

                is Categorization.Interior -> {
                    val baseCmt = category.cmt
                    val parent = computeFor(baseCmt) ?: return null
                    Extend(parent)
                }

                else -> null
            }
        }
    }
}

data class Usage(val path: UsagePath, val element: RsElement) {
    override fun toString(): String = "Usage($path)"
}

data class Declaration(val path: UsagePath.Base, val element: RsElement = path.declaration) {
    override fun toString(): String = "Declaration($path)"
}

class GatherUsageContext<DataFlow>(
    private val analysisData: UsageAnalysisData<DataFlow>
) : Delegate {

    override fun consume(element: RsElement, cmt: Cmt, mode: ConsumeMode) {
        analysisData.addUsage(element, cmt)
    }

    override fun matchedPat(pat: RsPat, cmt: Cmt, mode: MatchMode) {}

    override fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode) {
        pat.descendantsOfType<RsPatBinding>().forEach { binding ->
            analysisData.addDeclaration(binding)
        }
        analysisData.addUsage(pat, cmt)
    }

    override fun declarationWithoutInit(binding: RsPatBinding) {
        analysisData.addDeclaration(binding)
    }

    override fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode) {
        if (mode == MutateMode.WriteAndRead) {
            analysisData.addUsage(assignmentElement, assigneeCmt)
        }
    }

    override fun useElement(element: RsElement, cmt: Cmt) {
        analysisData.addUsage(element, cmt)
    }

    fun gather(body: RsBlock, implLookup: ImplLookup, inference: RsInferenceResult): UsageAnalysisData<DataFlow> {
        val gatherVisitor = ExprUseWalker(this, MemoryCategorizationContext(implLookup, inference))
        gatherVisitor.consumeBody(body)
        return analysisData
    }
}
