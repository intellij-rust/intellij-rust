/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsTraitImplementationInspectionTest : RsInspectionsTestBase(RsTraitImplementationInspection::class) {

    fun `test no error unresolved`() = checkErrors("""
        trait T{
            type Item;
            fn ok_1(&self) -> Self::Item;
            fn ok_2(&self) -> Option<i32>;
        }
        struct S;
        impl T for S{
            type Item = i32;
            fn ok_1(&self) -> Unresolved{}
            fn ok_2(&self) -> Option<Unresolved>{}
        }
    """)

    fun `test type compatibility complex`() = checkErrors("""
        trait Iter{ type Item; }
        trait T{
            type Item;
            type Other;
            fn ok_1(&self) -> Self::Other;
            fn ok_2(&self) -> Self::Item;
            fn ok_3(&self) -> Self::Item;
            fn err_1(&self) -> Self::Item;
            fn err_2(&self) -> Self::Item;
            fn err_3<X:Iter<Item=()>>(&self,arg:X) -> Self;
            fn err_4<T1:Iter,T2>(&self,arg1:T1,arg2:T2);
        }
        struct S;
        impl<X:Iter<Item=()>> T for X{
            type Item = X::Item;
            type Other = X;
            fn ok_1(&self) -> Self::Other {}
            fn ok_2(&self) -> X::Item {}
            fn ok_3(&self) -> () {}
            fn err_1(&self) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `()`, found `i32`
        ">i32</error> {}
            fn err_2(&self) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `()`, found `X`
        ">Self::Other</error> {}
            fn err_3<Y:Iter<Item=()>>(&self,arg:Y) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `X`, found `Y`
        Note: a type parameter was expected, but a different one was found">Y</error>{}
            fn err_4<T1:Iter,T2>(&self,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `T1`, found `T2`
        Note: a type parameter was expected, but a different one was found">arg1:T2</error>,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `T2`, found `T1`
        Note: a type parameter was expected, but a different one was found">arg2:T1</error>) {}
        }
    """)

    fun `test type compat in blanket impl`() = checkErrors("""
        trait T{
            type Item;
            fn ok_1(&self) -> Self::Item;
            fn ok_2(&self) -> Self::Item;
            fn err_1(&self) -> Self::Item;
            fn err_2<Y>(&self,arg:Y) -> Self::Item;
            fn err_3<Z>(&self,arg:Z) -> Self::Item;

        }
        struct S;
        impl<X> T for X{
            type Item = X;
            fn ok_1(&self) -> Self::Item{}
            fn ok_2(&self) -> X{}
            fn err_1(&self) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `X`, found `i32`
        ">i32</error>{}
            fn err_2<Y>(&self,arg:Y) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `X`, found `Y`
        Note: a type parameter was expected, but a different one was found">Y</error>{}
            fn err_3<Y>(&self,arg:Y) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `X`, found `Y`
        Note: a type parameter was expected, but a different one was found">Y</error>{}
        }
    """)

    fun `test associate types compatibility`() = checkErrors("""
        trait T{
            type Item;
            fn ok_1(&self) -> Self::Item;
            fn ok_2(&self) -> Self::Item;
            fn err_1(&self) -> Self::Item;
        }
        struct S;
        impl T for S{
            type Item = i32;
            fn ok_1(&self) -> Self::Item {}
            fn ok_2(&self) -> i32 {}
            fn err_1(&self) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `i32`, found `u32`
        ">u32</error> {}
        }
    """)

    fun `test type parameters count E0049`() = checkErrors("""
        trait T{
            fn ok_1<T>(&self,x:&T);
            fn ok_2(&self);
            fn ok_3(&self);
            fn err_1(&self);
            fn err_2<T>(&self,x:&T);
        }
        struct S;
        impl T<i32> for S{
            fn ok_1<X>(&self,x:&X){}
            fn ok_2<>(&self){}
            fn ok_3(&self){}
            fn err_1<error descr="Method `err_1` has 1 type parameter but the declaration in trait `T` has 0 [E0049]"><T></error>(&self){}
            fn err_2<error descr="Method `err_2` has 2 type parameters but the declaration in trait `T` has 1 [E0049]"><T1,T2></error>(&self,x:&T){}
        }
    """)

    fun `test fn type compatibility generic trait E0053`() = checkErrors("""
        trait T<T>{
            fn ok_1(&self,x:&T) -> i32;
            fn err_1(&self,x:&T);
        }
        struct S;
        impl T<i32> for S{
            fn ok_1(&self,x:&i32) -> i32{}
    fn err_1(&self,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `i32`, found `u32`
">x:&u32</error>) {}
        }
    """)

    fun `test fn type compatibility E0053`() = checkErrors("""
        trait T{
            fn ok_1(&self,x:i32) -> i32;
            fn ok_2<T>(&self,x:&T) -> i32;
            fn ok_3(self:&mut Self);
            fn err_1(&self,x:i32) -> i32;
            fn err_2(&self,x:&i32) -> i32;
            fn err_3<T>(&self,x:&T) -> i32;
            fn err_4(&mut self);
            fn err_5(&self)->i32;
        }
        struct S;
        impl T for S{
            fn ok_1(&self,x:i32) -> i32{}
            fn ok_2<T>(&self,x:&T) -> i32{}
            fn ok_3(&mut self){}
            fn err_1(&self,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `i32`, found `u32`
        ">x:u32</error>) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `i32`, found `u32`
        ">u32</error>{}
            fn err_2(&self,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `&i32`, found `&mut i32`
        ">x:&mut i32</error>) -> <error descr="Return type is incompatible with declared in trait [E0053]Expected `i32`, found `u32`
        ">u32</error>{}
            fn err_3<T>(&self,<error descr="Argument type is incompatible with declared in trait [E0053]Expected `&T`, found `&mut T`
        ">x:&mut T</error>) -> i32{}
            fn err_4(<error descr="Self type is incompatible with declared in trait [E0053]Expected `&mut S`, found `&S`
        ">mut self:&Self</error>){}
            fn <error descr="Return type is incompatible with declared in trait [E0053]Expected `i32`, found `()`
        ">err_5</error>(&self){}
        }
    """)

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

    fun `test unknown method in trait impl E0407`() = checkErrors("""
        trait T {
            fn foo();
        }
        impl T for () {
            fn foo() {}
            fn <error descr="Method `quux` is not a member of trait `T` [E0407]">quux</error>() {}
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
}
