/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.miscExtensions

import com.intellij.testFramework.UsefulTestCase
import org.intellij.lang.annotations.Language
import org.rust.MockRustcVersion
import org.rust.RsTestBase
import org.rust.ide.miscExtensions.RsBreadcrumbsInfoProvider.Companion.ellipsis
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.descendantsOfType

class RsBreadcrumbsInfoProviderTest : RsTestBase() {

    fun `test multiple line breadcrumbs`() = doMultipleLineAvailableTests("""
        fn foo() {}
        fn generic<T: Copy>() -> i32 { }
        struct Foo<'a> {}
        enum E<T> where T: Copy {}
        trait T {}
        impl S {}
        impl S<S> for X<T> {}
        impl T for (i32, i32) {}
        macro_rules! foo {}
        const C: u32 = 1;
        type TA = u32;
    """, """
        foo()
        generic()
        Foo
        E
        T
        impl S
        S for X
        T for (i32, i32)
        foo!
        C
        TA
    """)

    fun `test breadcrumbs`() = doAvailableTest("""
        fn main() {
            {
                loop {
                    'my: while true {
                        for i in 0..5 {
                            if true {
                                match option {
                                    Some(x) => println!("{}"/*caret*/, x),
                                    _ => break,
                                }
                            }
                        }
                    }
                }
            }
        }
    """, """
        main()
        {...}
        loop
        'my: while true
        for i in 0..5
        if true
        match option
        Some(x) =>
    """)

    fun `test if else breadcrumbs`() = doAvailableTest("""
        fn main() {
            if true {
            } else {
                /*caret*/
            }
        }
    """, """
        main()
        if true
        else
    """)

    fun `test if block breadcrumbs`() = doAvailableTest("""
       fn main() {
            if /*caret*/ { }
       }
    """, """
        main()
        if {...}
    """)

    fun `test while block breadcrumbs`() = doAvailableTest("""
        fn main() {
            while /*caret*/ {

            }
        }
    """, """
        main()
        while {...}
    """)

    fun `test empty for breadcrumbs`() = doAvailableTest("""
        fn main() {
            for in /*caret*/{

            }
        }
    """, """
        main()
        for {...}
    """)

    fun `test lambda breadcrumbs`() = doAvailableTest("""
       fn main() {
            |a| {/*caret*/};
       }
    """, """
        main()
        |a| {...}
    """)

    fun `test long if expr truncation`() = doAvailableTest("""
        fn main() {
            if 1 > 2 && 3 > 4 && 1 > 2 && 3 > 4 {
                /*caret*/
            }
        }
    """, """
        main()
        if 1 > 2 && 3 > 4 $ellipsis
    """)

    fun `test long match expr truncation`() = doAvailableTest("""
        fn main() {
            match 1 > 2 && 3 > 4 && 1 > 2 && 3 > 4 {
                /*caret*/
            }
        }
    """, """
        main()
        match 1 > 2 && 3 > 4 $ellipsis
    """)

    fun `test long for expr truncation`() = doAvailableTest("""
        fn main() {
            for _ in 0..000000000000000000002 {
                /*caret*/
            }
        }
    """, """
        main()
        for _ in 0..000000000000$ellipsis
    """)

    fun `test block expr label`() = doAvailableTest("""
        fn main (){
            let _ = 'block: {
                for &v in container.iter() {
                    if v > 0 { break 'block v; }
                }
                /*caret*/
                0
            };
        }
    """, """
        main()
        'block: {...}
    """)

    fun `test loop label`() = doAvailableTest("""
        fn main() {
            'one: loop {
                /*caret*/
                return;
            }
        }
    """, """
        main()
        'one: loop
    """)

    fun `test for label`() = doAvailableTest("""
        fn main() {
            'one: for _ in 1..2 {
                /*caret*/
            }
        }
    """, """
        main()
        'one: for _ in 1..2
    """)

    fun `test while label`() = doAvailableTest("""
        fn main() {
            'one: while false {
                /*caret*/
            }
        }
    """, """
        main()
        'one: while false
    """)

    private fun doMultipleLineAvailableTests(@Language("Rust") content: String, info: String) {
        InlineFile(content)

        val provider = RsBreadcrumbsInfoProvider()
        val actual = myFixture.file.descendantsOfType<RsElement>()
            .filter { provider.acceptElement(it) }
            .joinToString(separator = "\n") { provider.getElementInfo(it) }

        UsefulTestCase.assertSameLines(info.trimIndent(), actual)
    }

    private fun doAvailableTest(@Language("Rust") content: String, info: String) {
        InlineFile(content.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = RsBreadcrumbsInfoProvider()
        val elements = generateSequence(element) { provider.getParent(it) }
            .filter { provider.acceptElement(it) }
            .toList()
            .asReversed()

        val crumbs = elements.joinToString(separator = "\n") { provider.getElementInfo(it) }
        UsefulTestCase.assertSameLines(info.trimIndent(), crumbs)
    }
}
