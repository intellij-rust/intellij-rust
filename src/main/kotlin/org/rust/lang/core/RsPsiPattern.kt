/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.patterns.*
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*

/**
 * Rust PSI tree patterns.
 */
object RsPsiPattern {
    private val STATEMENT_BOUNDARIES = TokenSet.create(SEMICOLON, LBRACE, RBRACE)

    /**
     * Source of attributes: [https://doc.rust-lang.org/1.30.0/reference/attributes.html]
     */
    private val STD_ATTRIBUTES: Set<String> = setOf(
        "crate_name",
        "crate_type",
        "no_builtins",
        "no_main",
        "no_start",
        "no_std",
        "recursion_limit",
        "windows_subsystem",

        "no_implicit_prelude",
        "path",

        "link_args",
        "link",
        "linked_from",

        "link_name",
        "linkage",

        "macro_use",
        "macro_use",
        "macro_reexport",
        "macro_export",
        "no_link",

        "proc_macro",
        "proc_macro_derive",
        "proc_macro_attribute",

        "export_name",
        "global_allocator",
        "link_section",
        "no_mangle",

        "deprecated",

        "doc",

        "test",
        "should_panic",

        "cfg",
        "cfg_attr",

        "allow",
        "deny",
        "forbid",
        "warn",

        "must_use",

        "cold",
        "inline",

        "derive"
    )

    const val META_ITEM_IDENTIFIER_DEPTH = 4

    val onStatementBeginning: PsiElementPattern.Capture<PsiElement> = psiElement().with(OnStatementBeginning())

    fun onStatementBeginning(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement().with(OnStatementBeginning(*startWords))

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RsStructItem>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RsEnumItem>()

    val onFn: PsiElementPattern.Capture<PsiElement> = onItem<RsFunction>()

    val onMod: PsiElementPattern.Capture<PsiElement> = onItem<RsModItem>() or onItem<RsModDeclItem>()

    val onStatic: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsConstant>()
        .with("onStaticCondition") { e -> e.kind == RsConstantKind.STATIC })

    val onStaticMut: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsConstant>()
        .with("onStaticMutCondition") { e -> e.kind == RsConstantKind.MUT_STATIC })

    val onMacro: PsiElementPattern.Capture<PsiElement> = onItem<RsMacro>()

    val onTupleStruct: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsStructItem>()
        .withChild(psiElement<RsTupleFields>()))

    val onCrate: PsiElementPattern.Capture<PsiElement> = onItem<RsFile>()
        .with("onCrateCondition") { e ->
            val file = e.containingFile.originalFile as RsFile
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
        return psiElement().withSuperParent(6, implBlock)
    }

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsFunction>()
        .withChild(psiElement<RsOuterAttr>().withText("#[test]")))

    val inAnyLoop: PsiElementPattern.Capture<PsiElement> =
        psiElement().inside(
            true,
            psiElement<RsBlock>().withParent(
                or(
                    psiElement<RsForExpr>(),
                    psiElement<RsLoopExpr>(),
                    psiElement<RsWhileExpr>()
                )
            ),
            psiElement<RsLambdaExpr>()
        )

    val derivedTraitMetaItem: PsiElementPattern.Capture<RsMetaItem> =
        psiElement<RsMetaItem>().withSuperParent(
            2,
            psiElement()
                .withSuperParent<RsStructOrEnumItemElement>(2)
                .with("deriveCondition") { e -> e is RsMetaItem && e.name == "derive" }
        )

    /**
     * Supposed to capture outer attributes names, like `attribute` in `#[attribute(par1, par2)]`.
     */
    val nonStdOuterAttributeMetaItem: PsiElementPattern.Capture<RsMetaItem> =
        psiElement<RsMetaItem>()
            .withSuperParent(2, RsOuterAttributeOwner::class.java)
            .with("nonStdAttributeCondition") { e -> e.name !in STD_ATTRIBUTES }

    val includeMacroLiteral: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
        .withParent(psiElement<RsIncludeMacroArgument>())

    val pathAttrLiteral: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
        .withParent(psiElement<RsMetaItem>()
            .withSuperParent(2, StandardPatterns.or(psiElement<RsModDeclItem>(), psiElement<RsModItem>()))
            .with("pathAttrCondition") { metaItem -> metaItem.name == "path" }
        )

    val whitespace: PsiElementPattern.Capture<PsiElement> = psiElement().whitespace()

    val error: PsiElementPattern.Capture<PsiErrorElement> = psiElement<PsiErrorElement>()

    private inline fun <reified I : RsDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent<I>(META_ITEM_IDENTIFIER_DEPTH)
    }

    private fun onItem(pattern: ElementPattern<out RsDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(META_ITEM_IDENTIFIER_DEPTH, pattern)
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

inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
    return psiElement(I::class.java)
}

inline fun <reified I : PsiElement> psiElement(contextName: String): PsiElementPattern.Capture<I> {
    return psiElement(I::class.java).with("putIntoContext") { e, context ->
        context?.put(contextName, e)
        true
    }
}

inline fun <reified I : PsiElement> PsiElementPattern.Capture<PsiElement>.withSuperParent(level: Int): PsiElementPattern.Capture<PsiElement> {
    return this.withSuperParent(level, I::class.java)
}

inline infix fun <reified I : PsiElement> ElementPattern<I>.or(pattern: ElementPattern<I>): PsiElementPattern.Capture<PsiElement> {
    return psiElement().andOr(this, pattern)
}

private val PsiElement.prevVisibleOrNewLine: PsiElement?
    get() = leftLeaves
        .filterNot { it is PsiComment || it is PsiErrorElement }
        .filter { it !is PsiWhiteSpace || it.textContains('\n') }
        .firstOrNull()

/**
 * Similar with [TreeElementPattern.afterSiblingSkipping]
 * but it uses [PsiElement.getPrevSibling] to get previous sibling elements
 * instead of [PsiElement.getChildren].
 */
fun <T : PsiElement, Self : PsiElementPattern<T, Self>> PsiElementPattern<T, Self>.withPrevSiblingSkipping(
    skip: ElementPattern<out T>,
    pattern: ElementPattern<out T>
): Self = with("withPrevSiblingSkipping") { e ->
    val sibling = e.leftSiblings.dropWhile { skip.accepts(it) }
        .firstOrNull() ?: return@with false
    pattern.accepts(sibling)
}

fun <T, Self : ObjectPattern<T, Self>> ObjectPattern<T, Self>.with(name: String, cond: (T) -> Boolean): Self =
    with(object : PatternCondition<T>(name) {
        override fun accepts(t: T, context: ProcessingContext?): Boolean = cond(t)
    })

fun <T, Self : ObjectPattern<T, Self>> ObjectPattern<T, Self>.with(name: String, cond: (T, ProcessingContext?) -> Boolean): Self =
    with(object : PatternCondition<T>(name) {
        override fun accepts(t: T, context: ProcessingContext?): Boolean = cond(t, context)
    })
