/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.RsBundle
import org.rust.ide.intentions.InvertIfIntention.Context.ContextWithElse
import org.rust.ide.intentions.InvertIfIntention.Context.ContextWithoutElse
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type
import org.rust.lang.utils.negate

/**
 * Intention handles two cases:
 * ## If with else
 * ```
 * if f {
 *     stmts1
 * } else {
 *     stmts2
 * }
 * ```
 * Just swaps `stmts1` and `stmts2`
 *
 * ## If without else
 * ```
 * {
 *     ...
 *     if f {
 *         stmts1
 *     }
 *     stmts2
 * }
 * stmts3
 * ```
 *
 * Here we can apply fix only if `stmts2` diverges AND (`stmts1` diverges OR `stmts2` is empty).
 * Result of the fix would be:
 * ```
 * if !f {
 *     stmts2
 *     /* return/continue if needed */
 * }
 * stmts1
 * ```
 */
class InvertIfIntention : RsElementBaseIntentionAction<InvertIfIntention.Context>() {
    override fun getFamilyName(): String = text
    override fun getText() = RsBundle.message("intention.name.invert.if.condition")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    sealed interface Context {
        val ifCondition: RsExpr

        data class ContextWithElse(
            val ifExpr: RsIfExpr,
            override val ifCondition: RsExpr,
            val thenBlock: RsBlock,
            val elseBlock: RsBlock?,
        ) : Context

        class ContextWithoutElse(
            val ifExpr: RsIfExpr,
            override val ifCondition: RsExpr,
            /**
             * - [ifExpr] if it is tail expr
             * - `ifExpr.parent` if stmt
             */
            val ifStmt: RsElement,
            val thenBlock: RsBlock,
            val thenBlockStmts: List<PsiElement>,

            val block: RsBlock,
            val nextStmts: List<PsiElement>,
        ) : Context
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        if (element != ifExpr.`if`) return null
        val condition = getSuitableCondition(ifExpr) ?: return null
        val conditionExpr = condition.expr ?: return null
        val thenBlock = ifExpr.block ?: return null
        val elseBlock = ifExpr.elseBranch?.block

        return if (elseBlock != null) {
            ContextWithElse(ifExpr, conditionExpr, thenBlock, elseBlock)
        } else {
            createContextWithoutElse(ifExpr, conditionExpr, thenBlock)
        }
    }

    private fun createContextWithoutElse(ifExpr: RsIfExpr, ifCondition: RsExpr, thenBlock: RsBlock): ContextWithoutElse? {
        val thenBlockStmts = thenBlock.lbrace.rightSiblings.takeWhile { it != thenBlock.rbrace }.toList()

        val (ifStmt, block) = when (val parent = ifExpr.parent) {
            // usual statement
            is RsExprStmt -> {
                val block = parent.parent as? RsBlock ?: return null
                parent to block
            }
            // tail expr
            is RsBlock -> ifExpr to parent
            else -> return null
        }
        val nextStmts = ifStmt.rightSiblings.takeWhile { it != block.rbrace }.toList()

        return ContextWithoutElse(ifExpr, ifCondition, ifStmt, thenBlock, thenBlockStmts, block, nextStmts)
            .takeIf { it.canApplyFix() }
    }

    private fun ContextWithoutElse.canApplyFix(): Boolean {
        val parent = block.parent
        val hasImplicitReturnOrContinue = parent is RsFunctionOrLambda || parent is RsLooplikeExpr

        val ifDiverges = thenBlockStmts.isDiverges()
        val nextDiverges = nextStmts.isDiverges() || hasImplicitReturnOrContinue
        val nextIsEmpty = !nextStmts.hasStmts()
        return nextDiverges && (ifDiverges || nextIsEmpty)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val negatedCondition = ctx.ifCondition.negate() as? RsExpr ?: return

        val newIfExpr = when (ctx) {
            is ContextWithElse -> handleWithElseBranch(negatedCondition, ctx)
            is ContextWithoutElse -> handleWithoutElseBranch(negatedCondition, ctx)
        } ?: return

        val newCondition = newIfExpr.condition!!.expr

        if (newCondition is RsUnaryExpr && newCondition.excl != null) {
            val binaryExpression = (newCondition.expr as? RsParenExpr)?.expr as? RsBinaryExpr ?: return
            DemorgansLawIntention().invoke(project, editor, binaryExpression.binaryOp)
        }
    }

    private fun handleWithElseBranch(negatedCondition: RsExpr, ctx: ContextWithElse): RsIfExpr? {
        val psiFactory = RsPsiFactory(negatedCondition.project)
        val elseBlock = ctx.elseBlock ?: return null
        val newIf = psiFactory.createIfElseExpression(negatedCondition, elseBlock, ctx.thenBlock)
        return ctx.ifExpr.replace(newIf) as RsIfExpr
    }

    private fun handleWithoutElseBranch(negatedCondition: RsExpr, ctx: ContextWithoutElse): RsIfExpr {
        val factory = RsPsiFactory(negatedCondition.project)
        val source = IfStmts(ctx.thenBlockStmts, ctx.nextStmts)
        val target = source
            .addSemicolonToControlFlowExprs(factory)
            .convertTailExprToReturn(ctx.block, factory)
            .addImplicitReturnOrContinue(ctx.block, factory)
            .swapSpaces()
            .swapBranches()
            .removeImplicitReturnOrContinue(ctx.block)
            .removeNestedReturnOrContinueIfNoNextStmts(ctx.block)
            .convertReturnToTailExpr(ctx.block, factory)
            .copyPsi()

        ctx.ifCondition.replace(negatedCondition)
        replacePsi(ctx, target)

        return ctx.ifExpr
    }

    private fun replacePsi(ctx: ContextWithoutElse, result: IfStmts) {
        ctx.thenBlock.deleteContinuousChildRange(ctx.thenBlockStmts)
        ctx.block.deleteContinuousChildRange(ctx.nextStmts)

        ctx.thenBlock.addAllAfter(result.thenBlockStmts, ctx.thenBlock.lbrace)
        ctx.block.addAllAfter(result.nextStmts, ctx.ifStmt)
    }

    private fun getSuitableCondition(ifExpr: RsIfExpr): RsCondition? =
        ifExpr.condition?.takeIf { it.expr?.descendantOfTypeOrSelf<RsLetExpr>() == null }
}

/**
 * if (...) {
 *   [thenBlockStmts]
 * }
 * [nextStmts]
 */
data class IfStmts(
    val thenBlockStmts: List<PsiElement>,
    val nextStmts: List<PsiElement>,
) {
    override fun toString(): String {
        val thenBlockText = thenBlockStmts.joinToString("") { it.text }
        val nextText = nextStmts.joinToString("") { it.text }
        return "if (...) {$thenBlockText}$nextText"
    }
}

/**
 * `{ return 1 }` => `{ return 1; }`
 * `{ break }` => `{ break; }`
 * `{ continue }` => `{ continue; }`
 */
private fun IfStmts.addSemicolonToControlFlowExprs(factory: RsPsiFactory): IfStmts =
    copy(
        thenBlockStmts = thenBlockStmts.addSemicolonToControlFlowExpr(factory),
        nextStmts = nextStmts.addSemicolonToControlFlowExpr(factory)
    )

private fun List<PsiElement>.addSemicolonToControlFlowExpr(factory: RsPsiFactory): List<PsiElement> {
    val tailStmt = filterIsInstance<RsExprStmt>().lastOrNull()?.takeIf { it.isTailStmt } ?: return this
    val expr = tailStmt.expr
    return if (expr is RsRetExpr || expr is RsContExpr || expr is RsBreakExpr) {
        replace(tailStmt, expr.wrapInStmt(factory))
    } else {
        this
    }
}

/** `fn foo() { 1 }` => `fn foo() { return 1; } */
private fun IfStmts.convertTailExprToReturn(block: RsBlock, factory: RsPsiFactory): IfStmts {
    if (block.parent !is RsFunctionOrLambda) return this
    val tailStmt = nextStmts.findLast { it is RsExprStmt && it.isTailStmt } as? RsExprStmt ?: return this
    if (tailStmt.expr.type is TyUnit) return this

    val retStmt = factory.createStatement("return ${tailStmt.text};")
    return copy(nextStmts = nextStmts.replace(tailStmt, retStmt))
}

private fun IfStmts.convertReturnToTailExpr(block: RsBlock, factory: RsPsiFactory): IfStmts {
    if (block.parent !is RsFunctionOrLambda) return this
    val lastStmt = nextStmts.filterIsInstance<RsExprStmt>().lastOrNull() ?: return this
    val lastExpr = lastStmt.expr
    if (lastExpr !is RsRetExpr) return this
    val expr = lastExpr.expr ?: return this
    val tailStmt = factory.tryCreateExprStmtWithoutSemicolon(expr.text) ?: return this
    return copy(nextStmts = nextStmts.replace(lastStmt, tailStmt))
}

/**
 * `fn foo() { ...; }` => `fn foo() { ...; return; }
 * `loop { ...; }` => `loop { ...; continue; }`
 */
private fun IfStmts.addImplicitReturnOrContinue(block: RsBlock, factory: RsPsiFactory): IfStmts {
    val lastStmt = nextStmts.filterIsInstance<RsExprStmt>().lastOrNull()?.expr
    if (lastStmt is RsMacroCall) return this
    if (lastStmt?.isDiverges() == true) return this
    val addedStmt = when (block.parent) {
        is RsFunctionOrLambda -> factory.createStatement("return;")
        is RsLooplikeExpr -> factory.createStatement("continue;")
        else -> return this
    }
    val newline = listOfNotNull(factory.createNewline().takeIf { lastStmt != null })
    return copy(nextStmts = nextStmts + addedStmt + newline)
}

private fun IfStmts.removeImplicitReturnOrContinue(block: RsBlock): IfStmts {
    if (nextStmts.any { it is RsExpr }) return this
    val nextStmts = removeLastReturnOrContinue(nextStmts, block)
    return copy(nextStmts = nextStmts)
}

/**
 * ```
 * fn foo() {
 *     if f {
 *         ...;
 *         return;  // removes this unneeded `return`
 *     }
 * }
 */
private fun IfStmts.removeNestedReturnOrContinueIfNoNextStmts(block: RsBlock): IfStmts {
    if (nextStmts.hasStmts()) return this
    val thenBlockStmts = removeLastReturnOrContinue(thenBlockStmts, block)
    return copy(thenBlockStmts = thenBlockStmts)
}

private fun removeLastReturnOrContinue(stmts: List<PsiElement>, block: RsBlock): List<PsiElement> {
    val lastStmt = stmts.filterIsInstance<RsExprStmt>().lastOrNull() ?: return stmts
    val lastExpr = lastStmt.expr
    val canRemove = when (block.parent) {
        is RsFunctionOrLambda -> lastExpr is RsRetExpr && lastExpr.expr == null
        is RsLooplikeExpr -> when (lastExpr) {
            is RsContExpr -> lastExpr.label == null
            is RsBreakExpr -> lastExpr.label == null
            else -> false
        }
        else -> false
    }
    return if (canRemove) {
        stmts.filter { it != lastStmt }
    } else {
        stmts
    }
}

private fun IfStmts.swapSpaces(): IfStmts {
    if (thenBlockStmts.size < 3 || nextStmts.size < 3) return this
    val ifFirst = thenBlockStmts.first()
    val ifLast = thenBlockStmts.last()
    val nextFirst = nextStmts.first()
    val nextLast = nextStmts.last()
    if (listOf(ifFirst, ifLast, nextFirst, nextLast).any { it !is PsiWhiteSpace }) return this
    return IfStmts(
        listOf(nextFirst) + thenBlockStmts.subList(1, thenBlockStmts.size - 1) + nextLast,
        listOf(ifFirst) + nextStmts.subList(1, nextStmts.size - 1) + ifLast,
    )
}

private fun IfStmts.swapBranches(): IfStmts =
    copy(thenBlockStmts = nextStmts, nextStmts = thenBlockStmts)

private fun IfStmts.copyPsi(): IfStmts = copy(
    thenBlockStmts = thenBlockStmts.map { it.copy() },
    nextStmts = nextStmts.map { it.copy() },
)

private fun List<PsiElement>.isDiverges(): Boolean = any(PsiElement::isDiverges)

private fun PsiElement.isDiverges() =
    when (this) {
        is RsExpr -> type == TyNever
        is RsExprStmt -> expr.type == TyNever
        else -> false
    }

private fun List<PsiElement>.hasStmts(): Boolean =
    any { it !is PsiWhiteSpace && it !is PsiComment }

private fun RsExpr.wrapInStmt(factory: RsPsiFactory): RsExprStmt =
    factory.tryCreateExprStmtWithSemicolon(text)!!

private fun RsBlock.deleteContinuousChildRange(stmts: List<PsiElement>) {
    if (stmts.isNotEmpty()) {
        deleteChildRange(stmts.first(), stmts.last())
    }
}

private fun RsElement.addAllAfter(elements: List<PsiElement>, anchor: PsiElement) {
    for (element in elements.asReversed()) {
        // Note that:
        // - return value of `addAfter` is incorrect when adding whitespace
        // - `addBefore` doesn't work when adding whitespace after comment
        addAfter(element, anchor)
    }
}

private fun <T> List<T>.replace(from: T, to: T): List<T> =
    map { if (it == from) to else it }
