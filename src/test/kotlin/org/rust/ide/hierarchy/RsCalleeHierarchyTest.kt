/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

class RsCalleeHierarchyTest : RsCallHierarchyTestBase() {
    override val type: HierarchyType = HierarchyType.Callee

    fun `test no call`() = doTest("""
        fn /*caret*/foo() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)

    fun `test function`() = doTest("""
        fn /*caret*/foo() {
            bar()
        }
        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="bar()  (test_package)"/>
</node>
    """)

    fun `test multiple calls`() = doTest("""
        fn /*caret*/foo() {
            bar();
            bar()
        }
        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="bar() (2 usages)  (test_package)"/>
</node>
    """)

    fun `test multiple functions`() = doTest("""
        fn /*caret*/foo() {
            c();
            b();
            a();
        }
        fn a() {}
        fn b() {}
        fn c() {}
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="c()  (test_package)"/>
  <node text="b()  (test_package)"/>
  <node text="a()  (test_package)"/>
</node>
    """)

    fun `test tuple struct`() = doTest("""
        struct S(u32);

        fn /*caret*/foo() {
            S(0)
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="S()  (test_package)"/>
</node>
    """)

    fun `test enum variant`() = doTest("""
        enum E {
            A(u32)
        }

        fn /*caret*/foo() {
            E::A(0)
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="E::A()  (test_package)"/>
</node>
    """)

    fun `test macro`() = doTest("""
        macro_rules! mac {
            () => {}
        }

        fn /*caret*/foo() {
            mac!();
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="mac!()  (test_package)"/>
</node>
    """)

    fun `test method`() = doTest("""
        struct S;

        impl S {
            fn foo(&self) {}
        }

        fn /*caret*/foo(s: S) {
            s.foo();
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="S::foo()  (test_package)"/>
</node>
    """)

    fun `test associated method`() = doTest("""
        struct S;

        impl S {
            fn foo() {}
        }

        fn /*caret*/foo() {
            S::foo();
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="S::foo()  (test_package)"/>
</node>
    """)

    fun `test impl trait method`() = doTest("""
        struct S;

        trait Trait {
            fn foo(&self);
        }

        impl Trait for S {
            fn foo(&self) {}
        }

        fn /*caret*/foo(s: S) {
            s.foo();
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="&lt;S as Trait&gt;::foo()  (test_package)"/>
</node>
    """)

    fun `test generic trait method`() = doTest("""
        struct S;

        trait Trait {
            fn foo(&self) {}
        }

        fn /*caret*/foo<T: Trait>(t: T) {
            t.foo();
        }
    """, """
<node text="foo()  (test_package)" base="true">
  <node text="Trait::foo()  (test_package)"/>
</node>
    """)

    fun `test ignore nested function`() = doTest("""
        fn /*caret*/foo() {
            fn baz() {
                bar()
            }
        }

        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)

    fun `test ignore nested impl`() = doTest("""
        fn /*caret*/foo() {
            struct S;

            impl S {
                fn baz() {
                    bar()
                }
            }
        }

        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)

    fun `test ignore nested trait`() = doTest("""
        fn /*caret*/foo() {
            trait Trait {
                fn foo() {
                    bar()
                }
            }
        }

        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test ignore nested async block`() = doTest("""
        async fn /*caret*/foo() {
            let x = async {
                bar()
            };
        }

        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)

    fun `test ignore nested lambda`() = doTest("""
        fn /*caret*/foo() {
            let x = |a| {
                bar()
            };
        }

        fn bar() {}
    """, """
<node text="foo()  (test_package)" base="true"></node>
    """)
}
