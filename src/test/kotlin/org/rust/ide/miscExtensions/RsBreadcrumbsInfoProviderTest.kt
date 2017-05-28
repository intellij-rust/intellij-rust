package org.rust.ide.miscExtensions

import com.intellij.testFramework.UsefulTestCase
import org.rust.ide.utils.breadcrumbName
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsCompositeElement
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
        """)

        val actual = myFixture.file.descendantsOfType<RsCompositeElement>()
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
        """.trimIndent()

        UsefulTestCase.assertSameLines(expected, actual)
    }
}
