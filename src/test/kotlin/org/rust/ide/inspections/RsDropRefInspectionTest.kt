/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

/**
 * Tests for Drop Reference inspection.
 */
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsDropRefInspectionTest : RsInspectionsTestBase(RsDropRefInspection::class) {

    fun `test drop ref simple`() = checkByText("""
        fn main() {
            let val1 = Box::new(10);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&val1)</warning>;
        }
    """)

    fun `test drop ref full path`() = checkByText("""
        fn main() {
            let val1 = Box::new(20);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">std::mem::drop(&val1)</warning>;
        }
    """)

    fun `test drop ref aliased`() = checkByText("""
        use std::mem::drop as free;
        fn main() {
            let val1 = Box::new(30);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">free(&val1)</warning>;
        }
    """)

    fun `test drop ref ref type`() = checkByText("""
        fn foo() {}
        fn main() {
            let val = &foo();
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(val)</warning>;
        }
    """)

    fun `test drop ref shadowed`() = checkByText("""
        fn drop(val: &Box<u32>) {}
        fn main() {
            let val = Box::new(84);
            drop(&val); // This must not be highlighted
        }
    """)

    fun `test drop manually drop`() = checkByText("""
        use std::mem::ManuallyDrop;
        fn main() {
            let mut drop = ManuallyDrop::new("nodrop");
            unsafe {
                ManuallyDrop::drop(&mut drop); // This must not be highlighted
            }
        }
    """)

    fun `test drop ref method call`() = checkByText("""
        struct Foo;
        impl Foo {
            pub fn drop(&self, val: &Foo) {}
        }
        fn main() {
            let f = Foo;
            f.drop(&f); // This must not be highlighted
        }
    """)

    fun `test drop ref fix`() = checkFixByText("Remove &", """
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

    fun `test drop ref fix 2`() = checkFixByText("Remove &", """
        fn main() {
            let val1 = Box::new(40);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&   va<caret>l1)</warning>;
        }
    """, """
        fn main() {
            let val1 = Box::new(40);
            drop(val1);
        }
    """)

    fun `test ref mut drop fix`() = checkFixByText("Remove &mut", """
        fn main() {
            let val1 = Box::new(40);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&mut<caret> val1)</warning>;
        }
    """, """
        fn main() {
            let val1 = Box::new(40);
            drop(val1);
        }
    """)

    fun `test ref mut drop fix 2`() = checkFixByText("Remove &mut", """
        fn main() {
            let val1 = Box::new(40);
            <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">drop(&  mut<caret>  val1)</warning>;
        }
    """, """
        fn main() {
            let val1 = Box::new(40);
            drop(val1);
        }
    """)
}
