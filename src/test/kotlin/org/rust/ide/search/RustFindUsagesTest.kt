package org.rust.ide.search

import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase
import org.assertj.core.api.Assertions.*
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentOfType

class RustFindUsagesTest : RustTestCaseBase() {
    override val dataPath: String get() = ""

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
        val (element, @Suppress("UNUSED_VARIABLE") data) = configureAndFindElement(code)
        val source = element.parentOfType<RustNamedElement>()!!

        val usages = myFixture.findUsages(source)

        assertThat(usages.size).isEqualTo(expectedUsages)
    }
}
