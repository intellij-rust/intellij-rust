package org.rust.ide.inspections

/**
 * Tests for Drop Reference inspection.
 */
class RsDropRefInspectionTest : RsInspectionsTestBase(true) {

    fun testDropRefSimple() = checkByText<RsDropRefInspection>("""
        fn main() {
            let val1 = Box::new(10);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&val1)</warning>;
        }
    """)

    fun testDropRefFullPath() = checkByText<RsDropRefInspection>("""
        fn main() {
            let val1 = Box::new(20);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">std::mem::drop(&val1)</warning>;
        }
    """)

    fun testDropRefAliased() = checkByText<RsDropRefInspection>("""
        use std::mem::drop as free;
        fn main() {
            let val1 = Box::new(30);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">free(&val1)</warning>;
        }
    """)

    fun testDropRefRefType() = checkByText<RsDropRefInspection>("""
        fn foo() {}
        fn main() {
            let val = &foo();
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(val)</warning>;
        }
    """)

    fun testDropRefShadowed() = checkByText<RsDropRefInspection>("""
        fn drop(val: &Box<u32>) {}
        fn main() {
            let val = Box::new(84);
            drop(&val); // This must not be highlighted
        }
    """)

    fun testDropRefMethodCall() = checkByText<RsDropRefInspection>("""
        struct Foo;
        impl Foo {
            pub fn drop(&self, val: &Foo) {}
        }
        fn main() {
            let f = Foo;
            f.drop(&f); // This must not be highlighted
        }
    """)

    fun testDropRefFix() = checkFixByText<RsDropRefInspection>("Call with owned value", """
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
