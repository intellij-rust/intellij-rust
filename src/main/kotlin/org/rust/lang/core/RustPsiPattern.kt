package org.rust.lang.core

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.or
import org.rust.lang.core.completion.psiElement
import org.rust.lang.core.completion.withSuperParent
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile

/**
 * Rust PSI tree patterns.
 */
object RustPsiPattern {
    private val STATEMENT_BOUNDARIES = TokenSet.create(
        RustTokenElementTypes.SEMICOLON, RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE)

    class OnStatementBeginning : PatternCondition<PsiElement>("on statement beginning") {
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            val prev = t.prevVisibleOrNewLine ?: return true
            if (prev is PsiWhiteSpace) return true
            return prev.node.elementType in STATEMENT_BOUNDARIES
        }
    }

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RustStructItemElement>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RustEnumItemElement>()

    val onFn: PsiElementPattern.Capture<PsiElement> = onItem<RustFnItemElement>()

    val onMod: PsiElementPattern.Capture<PsiElement> = onItem<RustModItemElement>()

    val onStatic: PsiElementPattern.Capture<PsiElement> = onItem<RustStaticItemElement>()

    val onStaticMut: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement().with(
        object : PatternCondition<PsiElement>("onStaticMutCondition") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val elem = t.parent?.parent?.parent
                return (elem is RustStaticItemElement) && elem.mut != null
            }
        })

    val onMacro: PsiElementPattern.Capture<PsiElement> = onItem<RustMacroItemElement>()

    val onTupleStruct: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        .withSuperParent(3, PlatformPatterns.psiElement().withChild(psiElement<RustTupleFieldsElement>()))

    val onCrate: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement().withSuperParent<PsiFile>(3).with(
        object : PatternCondition<PsiElement>("onCrateCondition") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val file = t.containingFile.originalFile as RustFile
                return file.isCrateRoot
            }
        })

    val onExternBlock: PsiElementPattern.Capture<PsiElement> = onItem<RustForeignModItemElement>()

    val onExternBlockDecl: PsiElementPattern.Capture<PsiElement> =
            onItem<RustForeignFnDeclElement>() or
            onItem<RustForeignStaticDeclElement>() or
            onItem<RustForeignModItemElement>()

    val onAnyItem: PsiElementPattern.Capture<PsiElement> = onItem<RustDocAndAttributeOwner>()

    val onExternCrate: PsiElementPattern.Capture<PsiElement> = onItem<RustExternCrateItemElement>()

    val onTrait: PsiElementPattern.Capture<PsiElement> = onItem<RustTraitItemElement>()

    val onDropFn: PsiElementPattern.Capture<PsiElement> get() {
        val dropTraitRef = psiElement<RustTraitRefElement>().withText("Drop")
        val implBlock = psiElement<RustImplItemElement>().withChild(dropTraitRef)
        return PlatformPatterns.psiElement().withSuperParent(4, implBlock)
    }

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RustFnItemElement>()
        .withChild(psiElement<RustOuterAttrElement>().withText("#[test]")))

    inline fun <reified I : RustDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement().withSuperParent<I>(3)
    }

    private fun onItem(pattern: ElementPattern<out RustDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement().withSuperParent(3, pattern)
    }
}

val PsiElement.prevVisibleOrNewLine: PsiElement?
    get() = leftLeaves
        .filterNot { it is PsiComment || it is PsiErrorElement }
        .filter { it !is PsiWhiteSpace || it.textContains('\n') }
        .firstOrNull()

val PsiElement.leftLeaves: Sequence<PsiElement> get() = generateSequence(this, PsiTreeUtil::prevLeaf).drop(1)

val PsiElement.rightSiblings: Sequence<PsiElement> get() = generateSequence(this.nextSibling) { it.nextSibling }
