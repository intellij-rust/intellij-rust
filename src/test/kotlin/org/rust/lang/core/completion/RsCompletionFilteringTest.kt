/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCompletionFilteringTest: RsCompletionTestBase() {
    fun `test unsatisfied bound filtered 1`() = doSingleCompletion("""
        trait Bound {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S./*caret*/ }
    """, """
        trait Bound {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S.bar()/*caret*/ }
    """)

    fun `test unsatisfied bound filtered 2`() = doSingleCompletion("""
        trait Bound1 {}
        trait Bound2 {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound1> Trait1 for T {}
        impl<T: Bound2> Trait2 for T {}
        struct S;
        impl Bound1 for S {}
        fn main() { S./*caret*/ }
    """, """
        trait Bound1 {}
        trait Bound2 {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound1> Trait1 for T {}
        impl<T: Bound2> Trait2 for T {}
        struct S;
        impl Bound1 for S {}
        fn main() { S.foo()/*caret*/ }
    """)
}
