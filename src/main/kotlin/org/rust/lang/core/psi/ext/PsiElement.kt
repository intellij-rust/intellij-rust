/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
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


/**
 * Same as [ancestorStrict], but with "fake" parent links. See [org.rust.lang.core.macros.ExpansionResult].
 */
inline fun <reified T : PsiElement> PsiElement.contextStrict(): T? =
    PsiTreeUtil.getContextOfType(this, T::class.java, /* strict */ true)

/**
 * Same as [ancestorOrSelf], but with "fake" parent links. See [org.rust.lang.core.macros.ExpansionResult].
 */
inline fun <reified T : PsiElement> PsiElement.contextOrSelf(): T? =
    PsiTreeUtil.getContextOfType(this, T::class.java, /* strict */ false)


inline fun <reified T : PsiElement> PsiElement.childrenOfType(): List<T> =
    PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)


inline fun <reified T : PsiElement> PsiElement.descendantOfTypeStrict(): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, /* strict */ true)

inline fun <reified T : PsiElement> PsiElement.descendantsOfType(): Collection<T> =
    PsiTreeUtil.findChildrenOfType(this, T::class.java)

/**
 * Same as [PsiElement.getContainingFile], but return a "fake" file. See [org.rust.lang.core.macros.ExpansionResult].
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
