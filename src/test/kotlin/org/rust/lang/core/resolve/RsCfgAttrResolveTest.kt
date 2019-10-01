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

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test fn with cfg`() = checkByCode("""
        #[cfg(intellij_rust)]
        fn foo() {}
         //X
        #[cfg(not(intellij_rust))]
        fn foo() {}

        fn main() {
            foo();
          //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test struct field with cfg`() = checkByCode("""
        struct S {
            #[cfg(not(intellij_rust))] x: u32,
            #[cfg(intellij_rust)]      x: i32,
                                     //X
        }

        fn t(f: S) {
            f.x;
            //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test tuple struct field with cfg`() = checkByCodeGeneric<RsTupleFieldDecl>("""
        struct S(#[cfg(not(intellij_rust))] u32,
                 #[cfg(intellij_rust)]      i32);
                                          //X
        fn t(f: S) {
            f.0;
            //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test tuple enum variant with cfg`() = checkByCode("""
        enum E {
            #[cfg(not(intellij_rust))] Variant(u32),
            #[cfg(intellij_rust)]      Variant(u32),
                                     //X
        }

        fn t() {
            E::Variant(0);
             //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test inline mod with cfg`() = checkByCode("""
        #[cfg(intellij_rust)]
        mod my {
            pub fn bar() {}
                 //X
        }
        
        #[cfg(not(intellij_rust))]
        mod my {
            pub fn bar() {}
        }
        
        fn t() {
            my::bar();
              //^
        }
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test use item with cfg`() = checkByCode(""" 
        mod my {
            pub fn bar() {}
                 //X
            pub fn baz() {}
        }
        
        #[cfg(intellij_rust)]
        use my::bar as quux;
        
        #[cfg(not(intellij_rust))]
        use my::baz as quux;

        fn t() {
            quux();
          //^
        }
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro expansion with cfg`() = checkByCode(""" 
        struct S { x: i32 }
                 //X
                 
        macro_rules! my_macro {
            ($ t:ty) => { fn foobar() -> $ t {} };
        }

        #[cfg(intellij_rust)]
        my_macro!(S);
      
      
        #[cfg(not(intellij_rust))]
        my_macro!(i32);

        fn t() {
            foobar().x;
                   //^
        }
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test extern crate with cfg`() = stubOnlyResolve(""" 
    //- main.rs
        #[cfg(intellij_rust)]
        extern crate test_package;

        fn main() {
            test_package::hello();
        }               //^ lib.rs

    //- lib.rs
        pub fn hello() {}
             //X
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl`() = checkByCode(""" 
        struct S;
        #[cfg(intellij_rust)]
        impl S { fn foo(&self) {} }
                   //X
        #[cfg(not(intellij_rust))]
        impl S { fn foo(&self) {} }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in inline mod with cfg`() = checkByCode(""" 
        struct S;
        #[cfg(intellij_rust)]
        mod foo {
            impl super::S { fn foo(&self) {} }
                             //X
        }
        #[cfg(not(intellij_rust))]
        mod foo {
            impl super::S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in non-inline mod with cfg`() = stubOnlyResolve(""" 
    //- bar.rs
        impl super::S { fn foo(&self) {} }
    //- baz.rs
        impl super::S { fn foo(&self) {} }
    //- lib.rs
        struct S;
        
        #[cfg(intellij_rust)]
        #[path = "bar.rs"]
        mod foo;
        
        #[cfg(not(intellij_rust))]
        #[path = "baz.rs"]
        mod foo;
        
        fn main() {
            S.foo()
        }   //^ bar.rs
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in function body with cfg`() = checkByCode(""" 
        struct S;
        #[cfg(intellij_rust)]
        fn foo() {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(intellij_rust))]
        fn foo() {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl expanded from macro with cfg`() = checkByCode(""" 
        macro_rules! same {
            ($ i:item) => { $ i };
        }
        struct S;
        #[cfg(intellij_rust)]
        same! {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(intellij_rust))]
        same! {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg inside macros`() = checkByCode(""" 
        macro_rules! as_is { ($($ i:item)*) => { $($ i)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            mod spam { pub fn eggs() {} }
            #[cfg(not(intellij_rust))]
            pub use spam::*;

            #[cfg(intellij_rust)]
            mod spam {
                pub fn eggs() {}
            }        //X
            #[cfg(intellij_rust)]
            pub use spam::*;
        }
        fn main() {
            eggs();
        }  //^
     """)
}
