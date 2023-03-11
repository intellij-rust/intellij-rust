/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*

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
    ATTR_MACRO_AS_IS(
        "wrapped with `#[attr_as_is]` macro",
        AttrMacroTestWrappingImpl("attr_as_is")
    );

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

private class AttrMacroTestWrappingImpl(private val macroName: String) : TestWrappingImpl {
    override fun wrapProjectDescriptor(originalDescriptor: RustProjectDescriptorBase): RustProjectDescriptorBase? {
        return when {
            originalDescriptor is WithProcMacros -> null
            originalDescriptor === DefaultDescriptor -> WithProcMacroRustProjectDescriptor
            else -> WithProcMacros(originalDescriptor)
        }
    }

    override fun wrapCode(project: Project, code: String): Pair<String, TestUnwrapper?> {
        var caretMarker = ""
        var caretOffset = -1
        for (m in CARET_MARKERS) {
            caretMarker = m
            caretOffset = code.indexOf(m)
            if (caretOffset != -1) break
        }
        if (caretOffset == -1) {
            error("No /*caret*/ marker provided")
        }

        val file = RsPsiFactory(project, markGenerated = false, eventSystemEnabled = true)
            .createFile(code.replaceFirst(caretMarker, ""))
        val owner = file.findElementAt(caretOffset)
            ?.contexts
            ?.toList()
            ?.findLast { it is RsAttrProcMacroOwner }
            ?: return code to null

        val mutableText = StringBuilder(code)
        mutableText.insert(owner.startOffset, "#[test_proc_macros::$macroName]")

        return mutableText.toString() to TestUnwrapper(owner.startOffset)
    }

    companion object {
        private val CARET_MARKERS = listOf("/*caret*/", "<caret>", "<selection>")
    }
}

/**
 * Removes the [TestUnwrapper] applied by [TestWrapping.wrapCode]
 */
class TestUnwrapper(
    val offset: Int,
    var file: PsiFile? = null,
    var attr: SmartPsiElementPointer<RsAttr>? = null
) {
    fun init(file: PsiFile) {
        this.file = file
        val attr = file.findElementAt(offset)?.ancestorOrSelf<RsAttr>() ?: return
        this.attr = SmartPointerManager.createPointer(attr)
    }

    fun unwrap() {
        val file = file
        if (file != null) {
            PsiDocumentManager.getInstance(file.project).commitDocument(file.viewProvider.document)
        }
        val attr = attr?.element
        if (attr != null) {
            val toDelete = mutableListOf<PsiElement>(attr)
            attr.rightSiblings.takeWhile { it is PsiWhiteSpace }.toCollection(toDelete)
            runUndoTransparentWriteAction {
                for (element in toDelete.asReversed()) {
                    element.delete()
                }
            }
        }
    }
}
