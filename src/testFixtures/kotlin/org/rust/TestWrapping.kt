/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.prevLeafs
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.document

enum class TestWrapping(
    private val testDescription: String,
    private val impl: TestWrappingImpl
) {
    NONE("no wrapping", NoneTestWrappingImpl()),

    /**
     * Adds `#[attr_as_is]` attribute macro invocation to a top level item
     * under the caret marker. For example, this code snippet:
     * ```
     * fn foo() { /*caret*/ }
     * ```
     * will be transformed to
     * ```
     * #[attr_as_is]
     * fn foo() { /*caret*/ }
     * ```
     *
     * The macro `attr_as_is` is an "identity" macro. That is, it expands to its input, just like there isn't
     * a macro invocation.
     */
    ATTR_MACRO_AS_IS_AT_CARET(
        "wrapped with `#[attr_as_is]` macro",
        AttrMacroAtCaretTestWrappingImpl("attr_as_is")
    ),

    /**
     * Adds `#[attr_as_is]` attribute macro invocation to all top level items.
     * For example, this code snippet:
     * ```
     * fn foo() { /*caret*/ }
     * fn bar() { }
     * ```
     * will be transformed to
     * ```
     * #[attr_as_is]
     * fn foo() { /*caret*/ }
     * #[attr_as_is]
     * fn bar() { }
     * ```
     *
     * The macro `attr_as_is` is an "identity" macro. That is, it expands to its input, just like there isn't
     * a macro invocation.
     */
    ATTR_MACRO_AS_IS_ALL_ITEMS(
        "wrapped with `#[attr_as_is]` macro",
        AttrMacroAtAllItemsTestWrappingImpl("attr_as_is")
    ),
    ;

    override fun toString(): String = testDescription

    fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase? =
        impl.wrapProjectDescriptor(originalDescriptor)

    fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?> =
        impl.wrapCode(project, code)
}

private interface TestWrappingImpl {
    fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase?
    fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?>
}

private class NoneTestWrappingImpl : TestWrappingImpl {
    override fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase =
        originalDescriptor

    override fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?> =
        code to null
}

private class AttrMacroAtCaretTestWrappingImpl(private val macroName: String) : TestWrappingImpl {
    override fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase? =
        wrapProjectDescriptorWithProcMacros(originalDescriptor)

    override fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?> {
        return tryWrapCodeAtCaret(project, code, macroName) ?: error("No /*caret*/ marker provided")
    }

    companion object {
        private val CARET_MARKERS = listOf("/*caret*/", "<caret>", "<selection>")

        fun tryWrapCodeAtCaret(project: Project, code: String, macroName: String): Pair<String, TestUnwrapper?>? {
            var caretMarker = ""
            var caretOffset = -1
            for (m in CARET_MARKERS) {
                caretMarker = m
                caretOffset = code.indexOf(m)
                if (caretOffset != -1) break
            }
            if (caretOffset == -1) {
                return null
            }

            val file = RsPsiFactory(project, markGenerated = false, eventSystemEnabled = true)
                .createFile(code.replaceFirst(caretMarker, ""))
            val owner = file.findElementAt(caretOffset)
                ?.contexts
                ?.toList()
                ?.findLast { it is RsAttrProcMacroOwner }
                ?: return code to null

            val ownerStart = owner.prevLeafs.takeWhile { leaf -> leaf is PsiComment }.lastOrNull() ?: owner

            val mutableText = StringBuilder(code)
            mutableText.insert(ownerStart.startOffset, "#[test_proc_macros::$macroName]")

            return mutableText.toString() to TestUnwrapperImpl(ownerStart.startOffset)
        }
    }
}

private class AttrMacroAtAllItemsTestWrappingImpl(private val macroName: String) : TestWrappingImpl {
    override fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase? =
        wrapProjectDescriptorWithProcMacros(originalDescriptor)

    override fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?> {
        AttrMacroAtCaretTestWrappingImpl.tryWrapCodeAtCaret(project, code, macroName)?.let {
            return it
        }
        val file = RsPsiFactory(project, markGenerated = false, eventSystemEnabled = true)
            .createFile(code)
        val itemOffsets = file.childrenWithLeaves
            .filter { it is RsAttrProcMacroOwner && it.queryAttributes.langAttribute == null }
            .map { it.prevLeafs.takeWhile { leaf -> leaf is PsiComment }.lastOrNull() ?: it }
            .map { it.startOffset }
            .toList()
        val mutableText = StringBuilder(code)
        val insertion = "#[test_proc_macros::$macroName]"
        for ((i, offset) in itemOffsets.withIndex()) {
            mutableText.insert(offset + i * insertion.length, insertion)
        }
        val unwrappers = itemOffsets.withIndex().map { (i, offset) ->
            TestUnwrapperImpl(offset + i * insertion.length)
        }

        return mutableText.toString() to MultipleTestUnwrapper(unwrappers)
    }
}

/**
 * Removes the [TestUnwrapper] applied by [TestWrapping.wrapCode]
 */
interface TestUnwrapper {
    fun init(file: PsiFile)
    fun unwrap()
}

class TestUnwrapperImpl(
    val offset: Int,
    var file: PsiFile? = null,
    var range: RangeMarker? = null
): TestUnwrapper {
    override fun init(file: PsiFile) {
        this.file = file
        val document = file.document ?: return
        val leafAtOffset = file.findElementAt(offset) ?: return
        range = document.createRangeMarker(leafAtOffset.textRange)
    }

    override fun unwrap() {
        val file = file ?: return
        PsiDocumentManager.getInstance(file.project).commitDocument(file.viewProvider.document)
        val range = range ?: return
        val attr = file.findElementAt(range.startOffset)?.ancestorOrSelf<RsAttr>() ?: return

        val toDelete = mutableListOf<PsiElement>(attr)
        attr.rightSiblings.takeWhile { it is PsiWhiteSpace }.toCollection(toDelete)
        runUndoTransparentWriteAction {
            for (element in toDelete.asReversed()) {
                CodeEditUtil.allowToMarkNodesForPostponedFormatting(false)
                try {
                    element.delete()
                } finally {
                    CodeEditUtil.allowToMarkNodesForPostponedFormatting(true)
                }
            }
        }
    }
}

class MultipleTestUnwrapper(
    private val list: List<TestUnwrapper>
) : TestUnwrapper {
    override fun init(file: PsiFile) {
        for (unwrapper in list) {
            unwrapper.init(file)
        }
    }

    override fun unwrap() {
        for (unwrapper in list) {
            unwrapper.unwrap()
        }
    }
}


private fun wrapProjectDescriptorWithProcMacros(originalDescriptor: RustProjectDescriptorBase) = when {
    originalDescriptor is WithProcMacros -> null
    originalDescriptor === DefaultDescriptor -> WithProcMacroRustProjectDescriptor
    else -> WithProcMacros(originalDescriptor)
}
