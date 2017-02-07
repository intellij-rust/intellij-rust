package org.rust.ide.inspections

import org.junit.Test

class RsSelfInStaticContextInspectionTest : RsInspectionsTestBase(RsSelfInStaticContextInspection()) {
    @Test
    fun testWarningWhenSelfInStaticFunction() = checkByText("""
        struct Foo;

        impl Foo {
            fn foo() {
                let a = <error descr="The self keyword was used in a static method [E424]">self</error>;
            }
        }
    """)
}
