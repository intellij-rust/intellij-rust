/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

@ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
class RsDoctestAnnotatorTest : RsAnnotatorTestBase(RsDoctestAnnotator::class.java) {
    fun `test no injection 1`() = doTest("""
        |/// ``` ```
        |fn foo() {}
        |""")

    fun `test no injection 2`() = doTest("""
        |/// ```
        |/// ```
        |fn foo() {}
        |""")

    fun `test single line injection`() = doTest("""
        |/// ```
        |///<info> <inject>let a = 0;
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test multi line injection`() = doTest("""
        |/// ```
        |///<info> <inject>let a = 0;
        |</inject></info>///<info> <inject>let b = 0;
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test multi line injection with empty line`() = doTest("""
        |/// ```
        |///<info> <inject>let a = 0;
        |</inject></info>///<info>
        |</info>///<info> <inject>let a = 0;
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test acceptable "lang" string`() = doTest("""
        |/// ```rust, allow_fail, should_panic, no_run, test_harness, edition2018, edition2015
        |///<info> <inject>let a = 0;
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test no injection with unacceptable "lang" string`() = doTest("""
        |/// ```foobar
        |///let a = 0;
        |/// ```
        |fn foo() {}
        |""")

    fun `test no injection with unacceptable "lang" string contain acceptable parts`() = doTest("""
        |/// ```rust, foobar
        |///let a = 0;
        |/// ```
        |fn foo() {}
        |""")

    fun `test "# " escape`() = doTest("""
        |/// ```
        |///<info> # <inject>extern crate foobar;
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test "##" escape`() = doTest("""
        |/// ```
        |///<info> #<inject>#![allow(deprecated)]
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test "#" escape`() = doTest("""
        |/// ```
        |///<info> #<inject>
        |</inject></info>/// ```
        |fn foo() {}
        |""")

    fun `test no injection in non-lib target`() = checkByText("""
        /// ```
        /// let a = 0;
        /// ```
        fn foo() {}
    """, checkInfo = true)

    fun doTest(code: String) = checkByFileTree(
        "//- lib.rs\n/*caret*/${code.trimMargin()}",
        checkWarn = false,
        checkInfo = true,
        checkWeakWarn = false,
        ignoreExtraHighlighting = false
    )
}
