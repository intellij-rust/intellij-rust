/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.intellij.lang.annotations.Language
import org.rust.hasCaretMarker
import org.rust.openapiext.Testmark
import org.rust.replaceCaretMarker

abstract class RsCompletionTestFixtureBase<IN>(
    protected val myFixture: CodeInsightTestFixture
) : BaseFixture() {

    protected val project: Project get() = myFixture.project

    fun executeSoloCompletion() {
        val lookups = myFixture.completeBasic()

        if (lookups != null) {
            if (lookups.size == 1) {
                // for cases like `frob/*caret*/nicate()`,
                // completion won't be selected automatically.
                myFixture.type('\n')
                return
            }
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${lookups.size}\n"
                + lookups.joinToString("\n") { it.debug() })
        }
    }

    fun doFirstCompletion(code: IN, after: String) {
        check(hasCaretMarker(after))
        checkByText(code, after.trimIndent()) {
            val variants = myFixture.completeBasic()
            if (variants != null) {
                myFixture.type('\n')
            }
        }
    }

    fun doSingleCompletion(code: IN, after: String) {
        check(hasCaretMarker(after))
        checkByText(code, after.trimIndent()) { executeSoloCompletion() }
    }

    fun checkCompletion(
        lookupString: String,
        before: IN,
        @Language("Rust") after: String,
        completionChar: Char,
        testmark: Testmark?
    ) {
        val action = {
            checkByText(before, after.trimIndent()) {
                val items = myFixture.completeBasic()
                    ?: return@checkByText // single completion was inserted
                val lookupItem = items.find { it.lookupString == lookupString } ?: error("Lookup string $lookupString not found")
                myFixture.lookup.currentItem = lookupItem
                myFixture.type(completionChar)
            }
        }
        if (testmark != null) {
            testmark.checkHit(action)
        } else {
            action()
        }
    }

    fun checkNoCompletion(code: IN) {
        prepare(code)
        noCompletionCheck()
    }

    protected fun noCompletionCheck() {
        val lookups = myFixture.completeBasic()
        checkNotNull(lookups) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(lookups.isEmpty()) {
            "Expected zero completions, got ${lookups.size}."
        }
    }

    fun checkContainsCompletion(
        code: IN,
        variants: Iterable<String>,
        render: LookupElement.() -> String = { lookupString }
    ) {
        prepare(code)
        doContainsCompletion(variants.toSet(), render)
    }

    fun doContainsCompletion(variants: Set<String>, render: LookupElement.() -> String) {
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that contain $variants, but no completions found"
        }
        val renderedLookups = lookups.map { it.render() }
        for (variant in variants) {
            if (variant !in renderedLookups) {
                error("Expected completions that contain $variant, but got ${lookups.map { it.render() }}")
            }
        }
    }

    fun checkNotContainsCompletion(
        code: IN,
        variants: Set<String>,
        render: LookupElement.() -> String = { lookupString }
    ) {
        prepare(code)
        val lookups = myFixture.completeBasic()
        checkNotNull(lookups) {
            "Expected completions that don't contain $variants, but no completions found"
        }
        if (lookups.any { it.render() in variants }) {
            error("Expected completions that don't contain $variants, but got ${lookups.map { it.render() }}")
        }
    }

    fun checkContainsCompletionPrefixes(code: IN, prefixes: List<String>) {
        prepare(code)
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that start with $prefixes, but no completions found"
        }
        for (prefix in prefixes) {
            if (lookups.all { it.lookupString.startsWith(prefix) }) {
                error("Expected completions that start with $prefix, but got ${lookups.map { it.lookupString }}")
            }
        }
    }

    protected fun checkByText(code: IN, after: String, action: () -> Unit) {
        prepare(code)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected abstract fun prepare(code: IN)
}
