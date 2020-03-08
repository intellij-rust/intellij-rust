/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.SmartList
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsReplCodeFragment
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.findDescendantsWithMacrosOfAnyType

val PsiFileSystemItem.sourceRoot: VirtualFile?
    get() = virtualFile.let { ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(it) }

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) {
        if (it is PsiFile) null else it.parent
    }

val PsiElement.contexts: Sequence<PsiElement>
    get() = generateSequence(this) {
        if (it is PsiFile) null else it.context
    }

fun PsiElement.superParent(level: Int): PsiElement? {
    require(level > 0)
    return ancestors.drop(level).firstOrNull()
}

val PsiElement.ancestorPairs: Sequence<Pair<PsiElement, PsiElement>>
    get() {
        val parent = this.parent ?: return emptySequence()
        return generateSequence(Pair(this, parent)) { (_, parent) ->
            val grandPa = parent.parent
            if (parent is PsiFile || grandPa == null) null else parent to grandPa
        }
    }

val PsiElement.stubParent: PsiElement?
    get() {
        if (this is StubBasedPsiElement<*>) {
            val stub = this.greenStub
            if (stub != null) return stub.parentStub?.psi
        }
        return parent
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

inline fun <reified T : PsiElement> PsiElement.ancestorStrict(stopAt: Class<out PsiElement>): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, /* strict */ true, stopAt)

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


inline fun <reified T : PsiElement> PsiElement.childOfType(): T? =
    PsiTreeUtil.getChildOfType(this, T::class.java)

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

inline fun <reified T : PsiElement> PsiElement.descendantOfTypeOrSelf(): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, /* strict */ false)

inline fun <reified T : PsiElement> PsiElement.descendantsOfType(): Collection<T> =
    PsiTreeUtil.findChildrenOfType(this, T::class.java)

inline fun <reified T : PsiElement> PsiElement.descendantsOfTypeOrSelf(): Collection<T> =
    PsiTreeUtil.findChildrenOfAnyType(this, false, T::class.java)

inline fun <reified T : PsiElement> PsiElement.stubDescendantsOfTypeStrict(): Collection<T> =
    getStubDescendantsOfType(this, true, T::class.java)

inline fun <reified T : PsiElement> PsiElement.stubDescendantsOfTypeOrSelf(): Collection<T> =
    getStubDescendantsOfType(this, false, T::class.java)

inline fun <reified T : PsiElement> PsiElement.stubDescendantOfTypeOrStrict(): T? =
    getStubDescendantOfType(this, true, T::class.java)

inline fun <reified T : PsiElement> PsiElement.stubDescendantOfTypeOrSelf(): T? =
    getStubDescendantOfType(this, false, T::class.java)

fun <T : PsiElement> getStubDescendantsOfType(
    element: PsiElement?,
    strict: Boolean,
    aClass: Class<T>
): Collection<T> {
    if (element == null) return emptyList()
    val stub = (element as? PsiFileImpl)?.greenStub
        ?: (element as? StubBasedPsiElement<*>)?.greenStub
        ?: return PsiTreeUtil.findChildrenOfAnyType<T>(element, strict, aClass)

    val result = SmartList<T>()

    fun go(childrenStubs: List<StubElement<PsiElement>>) {
        for (childStub in childrenStubs) {
            val child = childStub.psi
            if (aClass.isInstance(child)) {
                result.add(aClass.cast(child))
            } else {
                go(childStub.childrenStubs)
            }
        }

    }

    if (strict) {
        go(stub.childrenStubs)
    } else {
        go(listOf(stub))
    }

    return result
}

fun <T : PsiElement> getStubDescendantOfType(
    element: PsiElement?,
    strict: Boolean,
    aClass: Class<T>
): T? {
    if (element == null) return null
    val stub = (element as? PsiFileImpl)?.greenStub
        ?: (element as? StubBasedPsiElement<*>)?.greenStub
        ?: return PsiTreeUtil.findChildOfType<T>(element, aClass, strict)

    fun go(childrenStubs: List<StubElement<PsiElement>>): T? {
        for (childStub in childrenStubs) {
            val child = childStub.psi
            if (aClass.isInstance(child)) {
                return aClass.cast(child)
            } else {
                go(childStub.childrenStubs)?.let { return it }
            }
        }

        return null
    }

    return if (strict) {
        go(stub.childrenStubs)
    } else {
        go(listOf(stub))
    }
}

inline fun <reified T : PsiElement> PsiElement.descendantsWithMacrosOfType(): Collection<T> =
    findDescendantsWithMacrosOfAnyType(this, true, T::class.java)

/**
 * Same as [PsiElement.getContainingFile], but return a "fake" file. See [org.rust.lang.core.macros.RsExpandedElement].
 */
val PsiElement.contextualFile: PsiFile
    get() {
        val file = contextOrSelf<PsiFile>() ?: error("Element outside of file: $text")
        return if (file is RsReplCodeFragment) {
            file.context.contextualFile
        } else {
            file
        }
    }

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

val PsiElement.startOffset: Int
    get() = textRange.startOffset

val PsiElement.endOffset: Int
    get() = textRange.endOffset

val PsiElement.endOffsetInParent: Int
    get() = startOffsetInParent + textLength

fun PsiElement.rangeWithPrevSpace(prev: PsiElement?) = when (prev) {
    is PsiWhiteSpace -> textRange.union(prev.textRange)
    else -> textRange
}

val PsiElement.rangeWithPrevSpace: TextRange
    get() = rangeWithPrevSpace(prevSibling)

val PsiElement.rangeWithSurroundingLineBreaks: TextRange
    get() {
        val startOffset = textRange.startOffset
        val endOffset = textRange.endOffset
        val text = containingFile.text
        val newLineBefore = text.lastIndexOf('\n', startOffset).takeIf { it >= 0 }?.let { it + 1 } ?: startOffset
        val newLineAfter = text.indexOf('\n', endOffset).takeIf { it >= 0 }?.let { it + 1 } ?: endOffset
        return TextRange(newLineBefore, newLineAfter)
    }

private fun PsiElement.getLineCount(): Int {
    val doc = containingFile?.let { file -> PsiDocumentManager.getInstance(project).getDocument(file) }
    if (doc != null) {
        val spaceRange = textRange ?: TextRange.EMPTY_RANGE

        if (spaceRange.endOffset <= doc.textLength) {
            val startLine = doc.getLineNumber(spaceRange.startOffset)
            val endLine = doc.getLineNumber(spaceRange.endOffset)

            return endLine - startLine
        }
    }

    return (text ?: "").count { it == '\n' } + 1
}

fun PsiWhiteSpace.isMultiLine(): Boolean = getLineCount() > 1

@Suppress("UNCHECKED_CAST")
inline val <T : StubElement<*>> StubBasedPsiElement<T>.greenStub: T?
    get() = (this as? StubBasedPsiElementBase<T>)?.greenStub
