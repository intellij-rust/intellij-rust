package org.rust.lang.core.completion

import com.intellij.patterns.ElementPattern
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustPatternTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun <T> testPattern(@Language("Rust") code: String, pattern: ElementPattern<T>) {
        val (elementAtCaret, @Suppress("UNUSED_VARIABLE") ignored) = configureAndFindElement(code)
        assertTrue(pattern.accepts(elementAtCaret))
    }
}
