/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.stubs.RsFileStub

val PsiElement.ancestors: Sequence<PsiElement> get() = generateSequence(this) {
    if (it is PsiFile) null else it.parent
}

val PsiElement.ancestorPairs: Sequence<Pair<PsiElement, PsiElement>> get() {
    val parent = this.parent ?: return emptySequence()
    return generateSequence(Pair(this, parent)) { (_, parent) ->
        val grandPa = parent.parent
        if (parent is PsiFile || grandPa == null) null else parent to grandPa
    }
}

val PsiElement.leftLeaves: Sequence<PsiElement>
    get() = generateSequence(this, PsiTreeUtil::prevLeaf).drop(1)

val PsiElement.rightSiblings: Sequence<PsiElement>
    get() = generateSequence(this.nextSibling) { it.nextSibling }

val PsiElement.leftSiblings: Sequence<PsiElement>
    get() = generateSequence(this.prevSibling) { it.prevSibling }

val PsiElement.childrenWithLeaves: Sequence<PsiElement>
    get() = generateSequence(this.firstChild) { it.nextSibling }

/**
 * Extracts node's element type
 */
val PsiElement.elementType: IElementType
    // XXX: be careful not to switch to AST
    get() = if (this is RsFile) RsFileStub.Type else PsiUtilCore.getElementType(this)


inline fun <reified T : PsiElement> PsiElement.ancestorStrict(): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, /* strict */ true)

inline fun <reified T : PsiElement> PsiElement.ancestorOrSelf(): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, /* strict */ false)

inline fun <reified T : PsiElement> PsiElement.ancestorOrSelf(stopAt: Class<out PsiElement>): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, /* strict */ false, stopAt)

inline fun <reified T : PsiElement> PsiElement.stubAncestorStrict(): T? =
    PsiTreeUtil.getStubOrPsiParentOfType(this, T::class.java)

/**
 * Same as [ancestorStrict], but with "fake" parent links. See [org.rust.lang.core.macros.RsExpandedElement].
 */
inline fun <reified T : PsiElement> PsiElement.contextStrict(): T? =
    PsiTreeUtil.getContextOfType(this, T::class.java, /* strict */ true)

/**
 * Same as [ancestorOrSelf], but with "fake" parent links. See [org.rust.lang.core.macros.RsExpandedElement].
 */
inline fun <reified T : PsiElement> PsiElement.contextOrSelf(): T? =
    PsiTreeUtil.getContextOfType(this, T::class.java, /* strict */ false)


inline fun <reified T : PsiElement> PsiElement.childrenOfType(): List<T> =
    PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)

inline fun <reified T : PsiElement> PsiElement.stubChildrenOfType(): List<T> {
    return if (this is PsiFileImpl) {
        stub?.childrenStubs?.mapNotNull { it.psi as? T } ?: return childrenOfType()
    } else {
        PsiTreeUtil.getStubChildrenOfTypeAsList(this, T::class.java)
    }
}

inline fun <reified T : PsiElement> PsiElement.descendantOfTypeStrict(): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, /* strict */ true)

inline fun <reified T : PsiElement> PsiElement.descendantsOfType(): Collection<T> =
    PsiTreeUtil.findChildrenOfType(this, T::class.java)

/**
 * Same as [PsiElement.getContainingFile], but return a "fake" file. See [org.rust.lang.core.macros.RsExpandedElement].
 */
val PsiElement.contextualFile: PsiFile
    get() = contextOrSelf() ?: error("Element outside of file: $text")

/**
 * Finds first sibling that is neither comment, nor whitespace before given element.
 */
fun PsiElement?.getPrevNonCommentSibling(): PsiElement? =
    PsiTreeUtil.skipSiblingsBackward(this, PsiWhiteSpace::class.java, PsiComment::class.java)

/**
 * Finds first sibling that is neither comment, nor whitespace after given element.
 */
fun PsiElement?.getNextNonCommentSibling(): PsiElement? =
    PsiTreeUtil.skipSiblingsForward(this, PsiWhiteSpace::class.java, PsiComment::class.java)

fun PsiElement.isAncestorOf(child: PsiElement): Boolean =
    child.ancestors.contains(this)

val PsiElement.endOffsetInParent: Int
    get() = startOffsetInParent + textLength

fun String.unescapeIdentifier(): String = removePrefix("r#")

val PsiElement.unescapedText: String get() {
    val text = text ?: return ""
    return if (this is LeafPsiElement) text.unescapeIdentifier() else text
}
