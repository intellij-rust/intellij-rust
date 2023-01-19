/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.usages.UsageViewSettings
import com.intellij.util.ThrowableRunnable
import com.intellij.util.xmlb.XmlSerializerUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.disableFindUsageTests
import org.rust.lang.core.psi.ext.RsNamedElement

class RsUsageViewTreeTest : RsTestBase() {

    private val originalSettings = UsageViewSettings()

    override fun setUp() {
        super.setUp()
        val settings = UsageViewSettings.instance
        XmlSerializerUtil.copyBean(settings.state, originalSettings)

        settings.isGroupByFileStructure = false
        settings.isGroupByModule = false
        settings.isGroupByPackage = false
        settings.isGroupByUsageType = true
        settings.isGroupByScope = false
    }

    override fun tearDown() {
        UsageViewSettings.instance.loadState(originalSettings)
        super.tearDown()
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (!disableFindUsageTests) {
            super.runTestRunnable(testRunnable)
        }
    }

    fun `test grouping function usages`() = doTestByText("""
        fn foo() {}
          //^

        fn bar() {
            foo();
        }

        fn baz() {
            foo();
        }
    """, """
        <root> (2)
         Function
          foo
         Usages in Project Files (2)
          function call (2)
           main.rs (2)
            6foo();
            10foo();
    """)

    fun `test grouping struct usages`() = doTestByText("""
        struct S {
             //^
            a: usize,
        }

        impl S {}
        impl S {}

        fn foo(s1: &S) {}

        fn bar() {
            let s1 = S { a: 1 };
            let a = 1;
            let s2 = S { a };
        }
    """, """
        <root> (5)
         Struct
          S
         Usages in Project Files (5)
          impl (2)
           main.rs (2)
            7impl S {}
            8impl S {}
          init struct (2)
           main.rs (2)
            13let s1 = S { a: 1 };
            15let s2 = S { a };
          type reference (1)
           main.rs (1)
            10fn foo(s1: &S) {}
    """)

    private fun doTestByText(@Language("Rust") code: String, representation: String) {
        InlineFile(code)
        val source = findElementInEditor<RsNamedElement>()

        val textRepresentation = myFixture.getUsageViewTreeTextRepresentation(source)
        assertEquals(representation.trimIndent(), textRepresentation.trimIndent())
    }
}
