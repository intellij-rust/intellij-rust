package org.rust.lang.core

import com.intellij.patterns.*
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.or
import org.rust.lang.core.completion.psiElement
import org.rust.lang.core.completion.withSuperParent
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.RsConstantKind
import org.rust.lang.core.psi.impl.mixin.kind

/**
 * Rust PSI tree patterns.
 */
object RsPsiPattern {
    private val STATEMENT_BOUNDARIES = TokenSet.create(
        RsTokenElementTypes.SEMICOLON, RsTokenElementTypes.LBRACE, RsTokenElementTypes.RBRACE)

    val onStatementBeginning: PsiElementPattern.Capture<PsiElement> = psiElement().with(OnStatementBeginning())

    fun onStatementBeginning(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement().with(OnStatementBeginning(*startWords))

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RsStructItem>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RsEnumItem>()

    val onFn: PsiElementPattern.Capture<PsiElement> = onItem<RsFunction>()

    val onMod: PsiElementPattern.Capture<PsiElement> = onItem<RsModItem>()

    val onStatic: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .with("onStaticCondition") {
            val elem = it.parent?.parent?.parent
            (elem is RsConstant) && elem.kind == RsConstantKind.STATIC
        }


    val onStaticMut: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .with("onStaticMutCondition") {
            val elem = it.parent?.parent?.parent
            (elem is RsConstant) && elem.kind == RsConstantKind.MUT_STATIC
        }

    val onMacro: PsiElementPattern.Capture<PsiElement> = onItem<RsMacroItem>()

    val onTupleStruct: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .withSuperParent(3, PlatformPatterns.psiElement().withChild(psiElement<RsTupleFields>()))

    val onCrate: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement().withSuperParent<PsiFile>(3)
        .with("onCrateCondition") {
            val file = it.containingFile.originalFile as RsFile
            file.isCrateRoot
        }

    val onExternBlock: PsiElementPattern.Capture<PsiElement> = onItem<RsForeignModItem>()

    val onExternBlockDecl: PsiElementPattern.Capture<PsiElement> =
        onItem<RsFunction>() or //FIXME: check if this is indeed a foreign function
            onItem<RsConstant>() or
            onItem<RsForeignModItem>()

    val onAnyItem: PsiElementPattern.Capture<PsiElement> = onItem<RsDocAndAttributeOwner>()

    val onExternCrate: PsiElementPattern.Capture<PsiElement> = onItem<RsExternCrateItem>()

    val onTrait: PsiElementPattern.Capture<PsiElement> = onItem<RsTraitItem>()

    val onDropFn: PsiElementPattern.Capture<PsiElement> get() {
        val dropTraitRef = psiElement<RsTraitRef>().withText("Drop")
        val implBlock = psiElement<RsImplItem>().withChild(dropTraitRef)
        return psiElement().withSuperParent(4, implBlock)
    }

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsFunction>()
        .withChild(psiElement<RsOuterAttr>().withText("#[test]")))

    val inAnyLoop: PsiElementPattern.Capture<PsiElement> =
        psiElement().inside(true,
            psiElement<RsBlock>().withParent(or(
                psiElement<RsForExpr>(),
                psiElement<RsLoopExpr>(),
                psiElement<RsWhileExpr>())),
            psiElement<RsLambdaExpr>())

    inline fun <reified I : RsDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent<I>(3)
    }

    private fun onItem(pattern: ElementPattern<out RsDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(3, pattern)
    }

    private class OnStatementBeginning(vararg startWords: String) : PatternCondition<PsiElement>("on statement beginning") {
        val myStartWords = startWords
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            val prev = t.prevVisibleOrNewLine
            return if (myStartWords.isEmpty())
                prev == null || prev is PsiWhiteSpace || prev.node.elementType in STATEMENT_BOUNDARIES
            else
                prev != null && prev.node.text in myStartWords
        }
    }
}

val PsiElement.prevVisibleOrNewLine: PsiElement?
    get() = leftLeaves
        .filterNot { it is PsiComment || it is PsiErrorElement }
        .filter { it !is PsiWhiteSpace || it.textContains('\n') }
        .firstOrNull()

val PsiElement.leftLeaves: Sequence<PsiElement> get() = generateSequence(this, PsiTreeUtil::prevLeaf).drop(1)

val PsiElement.rightSiblings: Sequence<PsiElement> get() = generateSequence(this.nextSibling) { it.nextSibling }

private fun <T, Self : ObjectPattern<T, Self>> ObjectPattern<T, Self>.with(name: String, cond: (T) -> Boolean): Self =
    with(object : PatternCondition<T>(name) {
        override fun accepts(t: T, context: ProcessingContext?): Boolean = cond(t)
    })
