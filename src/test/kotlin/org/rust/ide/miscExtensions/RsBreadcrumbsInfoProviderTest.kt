/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.miscExtensions

import com.intellij.testFramework.UsefulTestCase
import org.rust.ide.presentation.breadcrumbName
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.descendantsOfType


class RsBreadcrumbsInfoProviderTest : RsTestBase() {
    fun `test breadcrumbs`() {
        InlineFile("""
            fn foo() {}
            fn generic<T: Copy>() -> i32 { }
            struct Foo<'a> {}
            enum E<T> where T: Copy {}
            trait T {}
            impl S {}
            impl S<S> for X<T> {}
            impl T for (i32, i32) {}
            macro_rules! foo {}
        """)

        val actual = myFixture.file.descendantsOfType<RsElement>()
            .mapNotNull { breadcrumbName(it) }
            .joinToString(separator = "\n")

        val expected = """
            foo()
            generic()
            Foo
            E
            T
            impl S
            S for X
            T for (i32, i32)
            foo!
        """.trimIndent()

        UsefulTestCase.assertSameLines(expected, actual)
    }
}
