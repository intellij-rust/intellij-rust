package org.rust.lang.core

import com.intellij.patterns.*
import com.intellij.patterns.StandardPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.or
import org.rust.lang.core.completion.psiElement
import org.rust.lang.core.completion.withSuperParent
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.RustConstantKind
import org.rust.lang.core.psi.impl.mixin.kind

/**
 * Rust PSI tree patterns.
 */
object RustPsiPattern {
    private val STATEMENT_BOUNDARIES = TokenSet.create(
        RustTokenElementTypes.SEMICOLON, RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE)

    val onStatementBeginning: PsiElementPattern.Capture<PsiElement> = psiElement().with(OnStatementBeginning())

    fun onStatementBeginning(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement().with(OnStatementBeginning(*startWords))

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RustStructItemElement>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RustEnumItemElement>()

    val onFn: PsiElementPattern.Capture<PsiElement> = onItem<RustFunctionElement>()

    val onMod: PsiElementPattern.Capture<PsiElement> = onItem<RustModItemElement>()

    val onStatic: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .with("onStaticCondition") {
            val elem = it.parent?.parent?.parent
            (elem is RustConstantElement) && elem.kind == RustConstantKind.STATIC
        }


    val onStaticMut: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .with("onStaticMutCondition") {
            val elem = it.parent?.parent?.parent
            (elem is RustConstantElement) && elem.kind == RustConstantKind.MUT_STATIC
        }

    val onMacro: PsiElementPattern.Capture<PsiElement> = onItem<RustMacroItemElement>()

    val onTupleStruct: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .withSuperParent(3, PlatformPatterns.psiElement().withChild(psiElement<RustTupleFieldsElement>()))

    val onCrate: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement().withSuperParent<PsiFile>(3)
        .with("onCrateCondition") {
            val file = it.containingFile.originalFile as RustFile
            file.isCrateRoot
        }

    val onExternBlock: PsiElementPattern.Capture<PsiElement> = onItem<RustForeignModItemElement>()

    val onExternBlockDecl: PsiElementPattern.Capture<PsiElement> =
            onItem<RustFunctionElement>() or //FIXME: check if this is indeed a foreign function
            onItem<RustConstantElement>() or
            onItem<RustForeignModItemElement>()

    val onAnyItem: PsiElementPattern.Capture<PsiElement> = onItem<RustDocAndAttributeOwner>()

    val onExternCrate: PsiElementPattern.Capture<PsiElement> = onItem<RustExternCrateItemElement>()

    val onTrait: PsiElementPattern.Capture<PsiElement> = onItem<RustTraitItemElement>()

    val onDropFn: PsiElementPattern.Capture<PsiElement> get() {
        val dropTraitRef = psiElement<RustTraitRefElement>().withText("Drop")
        val implBlock = psiElement<RustImplItemElement>().withChild(dropTraitRef)
        return psiElement().withSuperParent(4, implBlock)
    }

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RustFunctionElement>()
        .withChild(psiElement<RustOuterAttrElement>().withText("#[test]")))

    val inAnyLoop: PsiElementPattern.Capture<PsiElement> =
        psiElement().inside(true,
            psiElement<RustBlockElement>().withParent(or(
                psiElement<RustForExprElement>(),
                psiElement<RustLoopExprElement>(),
                psiElement<RustWhileExprElement>())),
            psiElement<RustLambdaExprElement>())

    inline fun <reified I : RustDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent<I>(3)
    }

    private fun onItem(pattern: ElementPattern<out RustDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(3, pattern)
    }

    private class OnStatementBeginning(vararg startWords: String) : PatternCondition<PsiElement>("on statement beginning") {
        val myStartWords = startWords
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            val prev = t.prevVisibleOrNewLine ?: return true
            if (prev is PsiWhiteSpace) return true
            return myStartWords.isEmpty() && prev.node.elementType in STATEMENT_BOUNDARIES
                || myStartWords.isNotEmpty() && prev.node.text in myStartWords
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
