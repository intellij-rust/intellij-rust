/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnusedLabelsInspectionTest : RsInspectionsTestBase(RsUnusedLabelsInspection::class) {
    fun `test unused label with empty block`() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'label:/*warning**/ {}
        }
    """)

    fun `test unused label with while`() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'label:/*warning**/ while 0 == 0 {}
        }
    """)

    fun `test unused label with while let`() = checkByText("""
        fn main() {
            enum E<T> { A(T), B }
            let opt = E::A(0);
            /*warning descr="Unused label"*/'label:/*warning**/ while let E::A(_) = opt {}
        }
    """)

    fun `test unused label with for`() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'label:/*warning**/ for _ in 0..10 {}
        }
    """)

    fun `test unused label with loop`() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'label:/*warning**/ loop {}
        }
    """)

    fun `test no unused label with nested for`() = checkByText("""
        fn main() {
            'a: for _ in 0..10 {
                'b: for _ in 0..10 {
                    break 'b;
                }
                break 'a;
            }
        }
    """)

    fun `test unused label with nested for`() = checkByText("""
        fn main() {
            'a: for _ in 0..10 {
                /*warning descr="Unused label"*/'b:/*warning**/ for _ in 0..10 {
                    break 'a;
                }
            }

            /*warning descr="Unused label"*/'c:/*warning**/ for _ in 0..10 {
                'd: for _ in 0..10 {
                    break 'd;
                }
            }
        }
    """)

    fun `test unused label with loop and many breaks`() = checkByText("""
        fn main() {
            'label: loop {
                if true {
                    break 'label;
                } else {
                    break 'label;
                }
            }
        }
    """)

    fun `test unused label with nested for and shadowing`() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'a:/*warning**/ for _ in 0..2 {
                'a: for _ in 0..2 {
                    break 'a;
                }
            }
        }
    """)

    fun `test unused label with nested for `() = checkByText("""
        fn main() {
            /*warning descr="Unused label"*/'a:/*warning**/ for _ in 0..2 {
                'a: for _ in 0..2 {
                    break 'a;
                }
            }
        }
    """)

    fun `test unused label with use inside macro`() = checkByText("""
        macro_rules! mac_break { ($ lt:lifetime) => { break $ lt; } }
        fn main() {
            /*warning descr="Unused label"*/'shadowed:/*warning**/ for _ in 0..2 {
                'shadowed: for _ in 0..2 {
                    mac_break!('shadowed);
                }
            }

            'shadowed: for _ in 0..2 {
                mac_break!('shadowed);
                /*warning descr="Unused label"*/'shadowed:/*warning**/ for _ in 0..2 {}
            }
        }
    """)

    fun `test allow unused_labels`() = checkByText("""
        #[allow(unused_labels)]
        fn main() {
            'label: while 0 == 0 {}
        }
    """)

    fun `test allow unused`() = checkByText("""
        #[allow(unused)]
        fn main() {
            'label: while 0 == 0 {}
        }
    """)

    fun `test remove quick fix`() = checkFixByText("Remove `'label:`", """
        fn main() {
            /*warning descr="Unused label"*/'label:/*caret*//*warning**/ while 0 == 0 {}
        }
    """, """
        fn main() {
            /*caret*/while 0 == 0 {}
        }
    """)

    fun `test suppression quick fix for main fn`() = checkFixByText("Suppress `unused_labels` for fn main", """
        fn main() {
            /*warning descr="Unused label"*/'label:/*caret*//*warning**/ while 0 == 0 {}
        }
    """, """
        #[allow(unused_labels)]
        fn main() {
            'label:/*caret*/ while 0 == 0 {}
        }
    """)
}
