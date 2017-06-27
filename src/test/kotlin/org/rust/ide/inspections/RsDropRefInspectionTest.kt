/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Drop Reference inspection.
 */
class RsDropRefInspectionTest : RsInspectionsTestBase(RsDropRefInspection(), true) {

    fun testDropRefSimple() = checkByText("""
        fn main() {
            let val1 = Box::new(10);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&val1)</warning>;
        }
    """)

    fun testDropRefFullPath() = checkByText("""
        fn main() {
            let val1 = Box::new(20);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">std::mem::drop(&val1)</warning>;
        }
    """)

    fun testDropRefAliased() = checkByText("""
        use std::mem::drop as free;
        fn main() {
            let val1 = Box::new(30);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">free(&val1)</warning>;
        }
    """)

    fun testDropRefRefType() = checkByText("""
        fn foo() {}
        fn main() {
            let val = &foo();
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(val)</warning>;
        }
    """)

    fun testDropRefShadowed() = checkByText("""
        fn drop(val: &Box<u32>) {}
        fn main() {
            let val = Box::new(84);
            drop(&val); // This must not be highlighted
        }
    """)

    fun testDropRefMethodCall() = checkByText("""
        struct Foo;
        impl Foo {
            pub fn drop(&self, val: &Foo) {}
        }
        fn main() {
            let f = Foo;
            f.drop(&f); // This must not be highlighted
        }
    """)

    fun testDropRefFix() = checkFixByText("Call with owned value", """
        fn main() {
            let val1 = Box::new(40);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&va<caret>l1)</warning>;
        }
    """, """
        fn main() {
            let val1 = Box::new(40);
            drop(val1);
        }
    """)
}
