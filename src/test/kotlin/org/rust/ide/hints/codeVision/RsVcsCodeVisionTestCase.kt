/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.hints.VcsCodeVisionProvider
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.intellij.lang.annotations.Language


class RsVcsCodeVisionTestCase : CodeVisionTestCase() {
    fun `test function`() = doTest("""
        <# block [John Smith +2] #>
        fn foo() {}
    """)

    fun `test struct`() = doTest("""
        <# block [John Smith +2] #>
        struct S;
    """)

    fun `test enum`() = doTest("""
        <# block [John Smith +2] #>
        enum E { V1 }
    """)

    fun `test impl`() = doTest("""
        <# block [1 usage   John Smith +2] #>
        struct S;

        <# block [John Smith +2] #>
        impl S {}
    """)

    fun `test trait`() = doTest("""
        <# block [John Smith +2] #>
        trait Trait {}
    """)

    fun `test mod`() = doTest("""
        <# block [John Smith +2] #>
        mod foo {}
    """)

    fun `test macro`() = doTest("""
        <# block [John Smith +2] #>
        macro_rules! foo {}
    """)

    fun `test macro 2`() = doTest("""
        <# block [John Smith +2] #>
        macro foo() {}
    """)

    private fun doTest(@Language("Rust") text: String) {
        testProviders(text.trimIndent(), "main.rs", VcsCodeVisionProvider.id)
    }
}
