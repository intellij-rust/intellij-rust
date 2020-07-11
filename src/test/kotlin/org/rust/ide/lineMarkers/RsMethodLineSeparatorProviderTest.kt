/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings

class RsMethodLineSeparatorProviderTest : RsLineMarkerProviderTestBase() {

    fun `test impl`() {
        doTest("""
            struct Foo {} // - Has implementations
            impl Foo {
                const C1: i32 = 1;
                fn f1() {}
                fn f2() {}
                fn f3() { // - null
                }
                fn f4() { // - null
                }
                fn f5() {} // - null
        """)
    }

    fun `test trait`() {
        doTest("""
            trait Foo { // - Has implementations
                const C1: i32 = 1; // - Has implementations
                fn f1(); // - null,Has implementations
                fn f2(); // - null,Has implementations
                fn f3() { // - Has implementations
                }
                fn f4() { // - null,Has implementations
                }
                fn f5(); // - null,Has implementations
            }
        """)
    }

    fun `test extern`() {
        doTest("""
            extern {
                fn f1();
                fn f2();
                fn f3();
            }
        """)
    }

    fun `test top level`() {
        doTest("""
            const C1: i32 = 1;
            fn f1() {}
            fn f2() {}
            fn f3() { // - null
            }
            fn f4() { // - null
            }
            fn f5() {} // - null
        """)
    }

    fun `test not show`() {
        doTest("""
            const C1: i32 = 1;
            fn f1() {}
            fn f2() {}
            fn f3() {
            }
            fn f4() {
            }
            fn f5() {}
        """, false)
    }

    private fun doTest(source: String, showMethodSeparator: Boolean = true) {
        try {
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = showMethodSeparator
            doTestByText(source)
        } finally {
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = false
        }
    }
}
