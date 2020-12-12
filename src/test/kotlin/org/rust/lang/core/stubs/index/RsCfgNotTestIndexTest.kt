/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsMetaItem

class RsCfgNotTestIndexTest : RsTestBase() {
    fun `test simple cfg test`() = doTest("""
        #[cfg(test)]
            //^ false
        fn foo() {}
    """)

    fun `test simple cfg not test`() = doTest("""
        #[cfg(not(test))]
                //^ true
        fn foo() {}
    """)

    fun `test cfg test in complex condition`() = doTest("""
        #[cfg(and(windows, test))]
                         //^ false
        fn foo() {}
    """)

    fun `test cfg not test in complex condition`() = doTest("""
        #[cfg(not(and(windows, test)))]
                             //^ true
        fn foo() {}
    """)

    fun `test simple cfg_attr test`() = doTest("""
        #[cfg_attr(test, foo(bar))]
                 //^ false
        fn foo() {}
    """)

    fun `test simple cfg_attr not test`() = doTest("""
        #[cfg_attr(not(test), foo(bar))]
                     //^ true
        fn foo() {}
    """)

    fun `test cfg in cfg_attr test`() = doTest("""
        #[cfg_attr(windows, cfg(test))]
                              //^ false
        fn foo() {}
    """)

    fun `test cfg in cfg_attr not test`() = doTest("""
        #[cfg_attr(windows, cfg(not(test)))]
                                  //^ true
        fn foo() {}
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val (item, data) = findElementAndDataInEditor<RsMetaItem>()
        assertEquals(data.toBoolean(), RsCfgNotTestIndex.isCfgNotTest(item))
    }
}
