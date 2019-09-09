/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ExpandMacros
import org.rust.MockAdditionalCfgOptions
import org.rust.lang.core.psi.RsTupleFieldDecl

class RsCfgAttrResolveTest : RsResolveTestBase() {
    override val followMacroExpansions: Boolean get() = true

    @MockAdditionalCfgOptions("foo")
    fun `test fn with cfg`() = checkByCode("""
        #[cfg(foo)]
        fn foo() {}
         //X
        #[cfg(not(foo))]
        fn foo() {}

        fn main() {
            foo();
          //^
        }
    """)

    @MockAdditionalCfgOptions("foo")
    fun `test struct field with cfg`() = checkByCode("""
        struct S {
            #[cfg(not(foo))] x: u32,
            #[cfg(foo)]      x: i32,
                           //X
        }

        fn t(f: S) {
            f.x;
            //^
        }
    """)

    @MockAdditionalCfgOptions("foo")
    fun `test tuple struct field with cfg`() = checkByCodeGeneric<RsTupleFieldDecl>("""
        struct S(#[cfg(not(foo))] u32,
                 #[cfg(foo)]      i32);
                                //X
        fn t(f: S) {
            f.0;
            //^
        }
    """)

    @MockAdditionalCfgOptions("foo")
    fun `test tuple enum variant with cfg`() = checkByCode("""
        enum E {
            #[cfg(not(foo))] Variant(u32),
            #[cfg(foo)]      Variant(u32),
                           //X
        }

        fn t() {
            E::Variant(0);
             //^
        }
    """)

    @MockAdditionalCfgOptions("foo")
    fun `test inline mod with cfg`() = checkByCode("""
        #[cfg(foo)]
        mod my {
            pub fn bar() {}
                 //X
        }
        
        #[cfg(not(foo))]
        mod my {
            pub fn bar() {}
        }
        
        fn t() {
            my::bar();
              //^
        }
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test use item with cfg`() = checkByCode(""" 
        mod my {
            pub fn bar() {}
                 //X
            pub fn baz() {}
        }
        
        #[cfg(foo)]
        use my::bar as quux;
        
        #[cfg(not(foo))]
        use my::baz as quux;

        fn t() {
            quux();
          //^
        }
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("foo")
    fun `test macro expansion with cfg`() = checkByCode(""" 
        struct S { x: i32 }
                 //X
                 
        macro_rules! my_macro {
            ($ t:ty) => { fn foobar() -> $ t {} };
        }

        #[cfg(foo)]
        my_macro!(S);
      
      
        #[cfg(not(foo))]
        my_macro!(i32);

        fn t() {
            foobar().x;
                   //^
        }
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test extern crate with cfg`() = stubOnlyResolve(""" 
    //- main.rs
        #[cfg(foo)]
        extern crate test_package;

        fn main() {
            test_package::hello();
        }               //^ lib.rs

    //- lib.rs
        pub fn hello() {}
             //X
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test impl`() = checkByCode(""" 
        struct S;
        #[cfg(foo)]
        impl S { fn foo(&self) {} }
                   //X
        #[cfg(not(foo))]
        impl S { fn foo(&self) {} }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test impl in inline mod with cfg`() = checkByCode(""" 
        struct S;
        #[cfg(foo)]
        mod foo {
            impl super::S { fn foo(&self) {} }
                             //X
        }
        #[cfg(not(foo))]
        mod foo {
            impl super::S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test impl in non-inline mod with cfg`() = stubOnlyResolve(""" 
    //- bar.rs
        impl super::S { fn foo(&self) {} }
    //- baz.rs
        impl super::S { fn foo(&self) {} }
    //- lib.rs
        struct S;
        
        #[cfg(foo)]
        #[path = "bar.rs"]
        mod foo;
        
        #[cfg(not(foo))]
        #[path = "baz.rs"]
        mod foo;
        
        fn main() {
            S.foo()
        }   //^ bar.rs
     """)

    @MockAdditionalCfgOptions("foo")
    fun `test impl in function body with cfg`() = checkByCode(""" 
        struct S;
        #[cfg(foo)]
        fn foo() {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(foo))]
        fn foo() {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("foo")
    fun `test impl expanded from macro with cfg`() = checkByCode(""" 
        macro_rules! same {
            ($ i:item) => { $ i };
        }
        struct S;
        #[cfg(foo)]
        same! {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(foo))]
        same! {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)
}
