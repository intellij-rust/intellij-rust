/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockAdditionalCfgOptions

class RsTraitImplementationInspectionTest : RsInspectionsTestBase(RsTraitImplementationInspection::class) {

    fun `test self in trait not in impl E0186`() = checkErrors("""
        trait T {
            fn ok_foo(&self, x: u32);
            fn ok_bar(&mut self);
            fn ok_baz(self);
            fn foo(&self, x: u32);
            fn bar(&mut self);
            fn baz(self, o: bool);
        }
        struct S;
        impl T for S {
            fn ok_foo(&self, x: u32) {}
            fn ok_bar(&mut self) {}
            fn ok_baz(self) {}
            fn foo<error descr="Method `foo` has a `&self` declaration in the trait, but not in the impl [E0186]">(x: u32)</error> {}
            fn bar<error descr="Method `bar` has a `&mut self` declaration in the trait, but not in the impl [E0186]">()</error> {}
            fn baz<error descr="Method `baz` has a `self` declaration in the trait, but not in the impl [E0186]">(o: bool)</error> {}
        }
    """)

    fun `test self in impl not in trait E0185`() = checkErrors("""
        trait T {
            fn ok_foo(&self, x: u32);
            fn ok_bar(&mut self);
            fn ok_baz(self);
            fn foo(x: u32);
            fn bar();
            fn baz(o: bool);
        }
        struct S;
        impl T for S {
            fn ok_foo(&self, x: u32) {}
            fn ok_bar(&mut self) {}
            fn ok_baz(self) {}
            fn foo(<error descr="Method `foo` has a `&self` declaration in the impl, but not in the trait [E0185]">&self</error>, x: u32) {}
            fn bar(<error descr="Method `bar` has a `&mut self` declaration in the impl, but not in the trait [E0185]">&mut self</error>) {}
            fn baz(<error descr="Method `baz` has a `self` declaration in the impl, but not in the trait [E0185]">self</error>, o: bool) {}
        }
    """)

    fun `test incorrect params number in trait impl E0050`() = checkErrors("""
        trait T {
            fn ok_foo();
            fn ok_bar(a: u32, b: f64);
            fn foo();
            fn bar(a: u32);
            fn baz(a: u32, b: bool, c: f64);
            fn boo(&self, o: isize);
        }
        struct S;
        impl T for S {
            fn ok_foo() {}
            fn ok_bar(a: u32, b: f64) {}
            fn foo<error descr="Method `foo` has 1 parameter but the declaration in trait `T` has 0 [E0050]">(a: u32)</error> {}
            fn bar<error descr="Method `bar` has 2 parameters but the declaration in trait `T` has 1 [E0050]">(a: u32, b: bool)</error> {}
            fn baz<error descr="Method `baz` has 0 parameters but the declaration in trait `T` has 3 [E0050]">()</error> {}
            fn boo<error descr="Method `boo` has 2 parameters but the declaration in trait `T` has 1 [E0050]">(&self, o: isize, x: f16)</error> {}
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test incorrect params number in trait impl E0050 with cfg attributes`() = checkErrors("""
        trait T {
            fn foo(#[cfg(intellij_rust)] a: i32);
            fn bar(#[cfg(not(intellij_rust))] a: i32);
            fn baz(#[cfg(intellij_rust)] a: i32, #[cfg(not(intellij_rust))] a: u32);
            fn quux(#[cfg(not(intellij_rust))] a: i32, #[cfg(intellij_rust)] a: u32);
        }
        struct S1;
        impl T for S1 {
            fn foo(a: i32) {}
            fn bar() {}
            fn baz(a: i32);
            fn quux(a: u32);
        }
        struct S2;
        impl T for S2 {
            fn foo(#[cfg(intellij_rust)] a: i32) {}
            fn bar(#[cfg(not(intellij_rust))] a: i32) {}
            fn baz(#[cfg(intellij_rust)] a: i32, #[cfg(not(intellij_rust))] a: i32) {}
            fn quux(#[cfg(not(intellij_rust))] a: i32, #[cfg(intellij_rust)] a: u32);
        }
    """)

    fun `test absent method in trait impl E0046`() = checkErrors("""
        trait TError {
            fn bar();
            fn baz();
            fn boo();
        }
        <error descr="Not all trait items implemented, missing: `bar`, `boo` [E0046]">impl TError for ()</error> {
            fn baz() {}
        }
    """)

    fun `test absent method in negative trait impl`() = checkErrors("""
        trait TError {
            fn bar();
            fn baz();
            fn boo();
        }
        impl !TError for (){}
    """)

    fun `test unknown member in trait impl E0407, E0437, E0438`() = checkErrors("""
        trait T {
            fn method_existing();
            type TypeExisting;
            const CONST_EXISTING: i32;
        }
        impl T for () {
            fn method_existing() {}
            type TypeExisting = i32;
            const CONST_EXISTING: i32 = 0;
            fn /*error descr="Method `method_unknown` is not a member of trait `T` [E0407]"*/method_unknown/*error**/() {}
            type /*error descr="Type `TypeUnknown` is not a member of trait `T` [E0437]"*/TypeUnknown/*error**/ = i32;
            const /*error descr="Const `CONST_UNKNOWN` is not a member of trait `T` [E0438]"*/CONST_UNKNOWN/*error**/: i32 = 0;
        }
    """)

    fun `test mismatch member in trait impl E0323, E0324, E0325`() = checkErrors("""
        trait T {
            fn method1();
            fn method2();
            fn method3();

            type Type1;
            type Type2;
            type Type3;

            const CONST1: i32;
            const CONST2: i32;
            const CONST3: i32;
        }
        /*error descr="Not all trait items implemented, missing: `method2`, `method3`, `Type1`, `Type3`, `CONST1`, `CONST2` [E0046]"*/impl T for ()/*error**/ {
            fn method1() {}
            fn /*error descr="item `Type1` is an associated method, which doesn't match its trait `T` [E0324]"*/Type1/*error**/() {}
            fn /*error descr="item `CONST1` is an associated method, which doesn't match its trait `T` [E0324]"*/CONST1/*error**/() {}

            type Type2 = i32;
            type /*error descr="item `method2` is an associated type, which doesn't match its trait `T` [E0325]"*/method2/*error**/ = i32;
            type /*error descr="item `CONST2` is an associated type, which doesn't match its trait `T` [E0325]"*/CONST2/*error**/ = i32;

            const CONST3: i32 = 0;
            const /*error descr="item `method3` is an associated const, which doesn't match its trait `T` [E0323]"*/method3/*error**/: i32 = 0;
            const /*error descr="item `Type3` is an associated const, which doesn't match its trait `T` [E0323]"*/Type3/*error**/: i32 = 0;
        }
    """)

    fun `test ignore expanded methods`() = checkErrors("""
        macro_rules! as_is { ($($ t:tt)*) => {$($ t)*}; }
        trait T {
            fn foo1(&self);
            fn foo2();
            fn foo3(a: i32);
        }
        impl T for () {
            as_is! { fn foo1() {} }       // self in trait not in impl E0186
            as_is! { fn foo2(&self) {} }  // self in impl not in trait E0185
            as_is! { fn foo3() {} }       // incorrect params number in trait impl E0050
            as_is! { fn bar() {} }        // unknown method E0407
        }
    """)

    fun `test different type items have same name E0046`() = checkErrors("""
        trait A {
            type C;
            const C: i32;
        }
        <error descr="Not all trait items implemented, missing: `C` [E0046]">impl A for ()</error> {
            type C = ();
        }
    """)

    fun `test const and type with same name`() = checkErrors("""
        trait T {
            const FOO: i32;
            type FOO;
        }
        impl T for () {
            const FOO: i32 = 0;
            type FOO = i32;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test ignore cfg-disabled item without a default`() = checkErrors("""
        trait A {
            #[cfg(intellij_rust)]
            fn foo() {}
            #[cfg(not(intellij_rust))]
            fn foo();
        }
        impl A for () {}
    """)
}
