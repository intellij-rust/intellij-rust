/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.openapi.util.Key
import com.intellij.patterns.*
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocLinkDestination

/**
 * Rust PSI tree patterns.
 */
object RsPsiPattern {
    private val STATEMENT_BOUNDARIES = TokenSet.create(SEMICOLON, LBRACE, RBRACE)

    private val LINT_ATTRIBUTES: Set<String> = setOf(
        "allow",
        "warn",
        "deny",
        "forbid"
    )

    val META_ITEM_ATTR: Key<RsAttr> = Key.create("META_ITEM_ATTR")

    /** @see RsMetaItem.isRootMetaItem */
    val rootMetaItem: PsiElementPattern.Capture<RsMetaItem> = rootMetaItem()

    val onStatementBeginning: PsiElementPattern.Capture<PsiElement> = psiElement().with(OnStatementBeginning())

    fun onStatementBeginning(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement().with(OnStatementBeginning(*startWords))

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RsStructItem>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RsEnumItem>()

    val onEnumVariant: PsiElementPattern.Capture<PsiElement> = onItem<RsEnumVariant>()

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

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RsFunction>()
        .withChild(psiElement<RsOuterAttr>().withText("#[test]")))

    val onStructLike: PsiElementPattern.Capture<PsiElement> = onStruct or onEnum or onEnumVariant

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
            rootMetaItem("derive", psiElement<RsStructOrEnumItemElement>())
        )

    /**
     * Supposed to capture outer attributes names, like `attribute` in `#[attribute(par1, par2)]`.
     */
    val nonStdOuterAttributeMetaItem: PsiElementPattern.Capture<RsMetaItem> =
        psiElement<RsMetaItem>()
            .with("nonBuiltinAttributeCondition") { e -> e.name !in RS_BUILTIN_ATTRIBUTES }
            .with(RootMetaItemCondition)
            .with("RsOuterAttr") { _, context ->
                context?.get(META_ITEM_ATTR) is RsOuterAttr
            }

    val lintAttributeMetaItem: PsiElementPattern.Capture<RsMetaItem> =
        rootMetaItem
            .with("lintAttributeCondition") { e -> e.name in LINT_ATTRIBUTES }

    val includeMacroLiteral: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
        .withParent(psiElement<RsIncludeMacroArgument>())

    val pathAttrLiteral: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
        .withParent(
            rootMetaItem("path", psiElement<RsModDeclItem>() or psiElement<RsModItem>())
        )

    val whitespace: PsiElementPattern.Capture<PsiElement> = psiElement().whitespace()

    val error: PsiElementPattern.Capture<PsiErrorElement> = psiElement<PsiErrorElement>()

    val simplePathPattern: ElementPattern<PsiElement>
        get() {
            val simplePath = psiElement<RsPath>()
                .with(object : PatternCondition<RsPath>("SimplePath") {
                    override fun accepts(path: RsPath, context: ProcessingContext?): Boolean =
                        path.kind == PathKind.IDENTIFIER &&
                            path.path == null &&
                            path.typeQual == null &&
                            !path.hasColonColon &&
                            path.ancestorStrict<RsUseSpeck>() == null &&
                            path.ancestorStrict<RsDocLinkDestination>() == null
                })
            return psiElement().withParent(simplePath)
        }

    /** `#[cfg()]` */
    private val cfgAttributeMeta: PsiElementPattern.Capture<RsMetaItem> = rootMetaItem("cfg")

    /** `#[cfg_attr()]` */
    private val cfgAttrAttributeMeta: PsiElementPattern.Capture<RsMetaItem> = rootMetaItem("cfg_attr")

    /** `#[doc(cfg())]` */
    private val docCfgAttributeMeta: PsiElementPattern.Capture<RsMetaItem> = metaItem("cfg")
        .withSuperParent(2, rootMetaItem("doc"))

    /**
     * ```
     * #[cfg_attr(condition, attr)]
     *           //^
     * ```
     */
    private val cfgAttrCondition: PsiElementPattern.Capture<RsMetaItem> = psiElement<RsMetaItem>()
        .withSuperParent(2, cfgAttrAttributeMeta)
        .with("firstItem") { it, _ -> (it.parent as? RsMetaItemArgs)?.metaItemList?.firstOrNull() == it }

    val anyCfgCondition: PsiElementPattern.Capture<RsMetaItem> = cfgAttrCondition or
        psiElement<RsMetaItem>()
            .withSuperParent(2, cfgAttributeMeta or docCfgAttributeMeta)

    val anyCfgFeature: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
        .withParent(metaItem("feature"))
        .inside(anyCfgCondition)

    /**
     * A leaf literal inside [anyCfgFeature] or an identifier at the same place
     * ```
     * #[cfg(feature = "foo")] // Works for "foo" (leaf literal)
     * #[cfg(feature = foo)]   // Works for "foo" (leaf identifier)
     * ```
     */
    val insideAnyCfgFeature: PsiElementPattern.Capture<PsiElement> = psiElement().withParent(anyCfgFeature) or
        psiElement(IDENTIFIER)
            .withParent(
                psiElement<RsCompactTT>()
                    .withParent(
                        psiElement<RsMetaItem>()
                            .inside(anyCfgCondition)
                    )
            )
            .with("feature") { it, _ ->
                val eq = it.getPrevNonCommentSibling()
                val feature = eq?.getPrevNonCommentSibling()
                eq?.elementType == EQ && feature?.textMatches("feature") == true
            }

    private inline fun <reified I : RsDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(2, rootMetaItem(ownerPattern = psiElement<I>()))
    }

    private fun onItem(pattern: ElementPattern<out RsDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(2, rootMetaItem(ownerPattern = pattern))
    }

    private fun metaItem(key: String): PsiElementPattern.Capture<RsMetaItem> =
        psiElement<RsMetaItem>().with("MetaItemName") { item, _ ->
            item.name == key
        }

    /**
     * @param key required attribute name. `null` means any root attribute
     * @param ownerPattern additional requirements for item owned the corresponding attribute
     *
     * @see RsMetaItem.isRootMetaItem
     * @see RsAttr.owner
     */
    private fun rootMetaItem(
        key: String? = null,
        ownerPattern: ElementPattern<out RsDocAndAttributeOwner>? = null
    ): PsiElementPattern.Capture<RsMetaItem> {
        val metaItemPattern = if (key == null) psiElement<RsMetaItem>() else metaItem(key)
        val rootMetaItem = metaItemPattern.with(RootMetaItemCondition)
        return if (ownerPattern != null) {
            rootMetaItem.with("ownerPattern") { _, context ->
                val attr = context?.get(META_ITEM_ATTR) ?: return@with false
                ownerPattern.accepts(attr.owner, context)
            }
        } else {
            rootMetaItem
        }
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

    /** @see RsMetaItem.isRootMetaItem */
    private object RootMetaItemCondition : PatternCondition<RsMetaItem>("rootMetaItem") {
        override fun accepts(meta: RsMetaItem, context: ProcessingContext?): Boolean {
            return meta.isRootMetaItem(context)
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

inline infix fun <reified I : PsiElement> ElementPattern<out I>.or(pattern: ElementPattern<out I>): PsiElementPattern.Capture<I> {
    return psiElement<I>().andOr(this, pattern)
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
