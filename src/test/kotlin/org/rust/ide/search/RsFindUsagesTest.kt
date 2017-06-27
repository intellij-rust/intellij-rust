/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement

class RsFindUsagesTest : RsTestBase() {
    fun testFindLocalVar() = doTest("""
        fn foo(x: i32) -> i32 {
             //^
            let y = x * 2;
            let x = x * 3 + y;
            x
        }
    """, 2)

    fun testFunction() = doTest("""
         fn foo() {}
           //^

         fn bar() { foo() }

         mod a {
             use super::foo;

             fn baz() { foo() }
         }

         mod b {
             fn foo() {}
             fn bar() { foo() }
         }
    """, 3)

    private fun doTest(@Language("Rust") code: String, expectedUsages: Int) {
        InlineFile(code)
        val source = findElementInEditor<RsNamedElement>()

        val usages = myFixture.findUsages(source)

        assertThat(usages.size).isEqualTo(expectedUsages)
    }
}
