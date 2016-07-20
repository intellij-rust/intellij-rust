package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustPatternTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun <T> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        val element = InlineFile(code).elementAtCaret<PsiElement>()
        assertTrue(pattern.accepts(element))
    }
}
