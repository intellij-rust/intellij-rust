package org.rust.lang.core.pattern

import com.intellij.patterns.ElementPattern
import org.rust.lang.RustTestCaseBase

abstract class RustPatternTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun <T>testPattern(code: String, pattern: ElementPattern<T>) {
        val (elementAtCaret, ignored) = configureAndFindElement(code)
        assertTrue(pattern.accepts(elementAtCaret))
    }
}
