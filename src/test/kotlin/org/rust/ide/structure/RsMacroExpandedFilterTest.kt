/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import org.intellij.lang.annotations.Language

class RsMacroExpandedFilterTest : RsStructureViewToggleableActionTest() {

    fun `test macro expanded filter`() = doTest("""
        pub enum Enum { One, Two }

        macro_rules! foo {
            () => {
                pub struct Struct {
                    field: bool,
                }

                pub trait Trait {
                    fn func(&self);
                }

                impl Trait for Struct {
                    fn func(&self) {}
                }
            };
        }

        foo! {}

        struct Persistent;
    """, """
        |-main.rs visibility=none
        | -Enum visibility=public
        |  One visibility=none
        |  Two visibility=none
        | foo visibility=none
        | -Struct visibility=public
        |  field: bool visibility=private
        | -Trait visibility=public
        |  func() visibility=none
        | -Trait for Struct visibility=none
        |  func() visibility=none
        | Persistent visibility=private
    """, """
        |-main.rs visibility=none
        | -Enum visibility=public
        |  One visibility=none
        |  Two visibility=none
        | foo visibility=none
        | Persistent visibility=private
    """)

    fun `test filter macro expanded nested functions`() = doTest("""
        macro_rules! nested {
            () => {
                fn first() {
                    pub fn second() {}

                    pub struct Inner(bool);
                }
            };
        }

        nested!();

        fn external() {
            nested!();
        }
    """, """
        |-main.rs visibility=none
        | nested visibility=none
        | -first() visibility=private
        |  second() visibility=public
        |  Inner visibility=public
        | -external() visibility=private
        |  -first() visibility=private
        |   second() visibility=public
        |   Inner visibility=public
    """, """
        |-main.rs visibility=none
        | nested visibility=none
        | external() visibility=private
    """)

    fun `test filter on macro expanded type definitions has no effect`() = doTest("""
        macro_rules! typemacro {
            ($ t: ty) => {Option<$ t>};
        }

        type X = typemacro!(u64);

        fn f(x: typemacro!(bool)) -> typemacro!(bool) { x }

        trait Trait {
            type T = typemacro!(bool);
        }

        struct Foo(typemacro!(bool));

        impl Trait for Foo {
            type T = typemacro!(bool);
        }
    """, """
        |-main.rs visibility=none
        | typemacro visibility=none
        | X visibility=private
        | f(typemacro!(bool)) -> typemacro!(bool) visibility=private
        | -Trait visibility=private
        |  T visibility=none
        | Foo visibility=private
        | -Trait for Foo visibility=none
        |  T visibility=none
    """, """
        |-main.rs visibility=none
        | typemacro visibility=none
        | X visibility=private
        | f(typemacro!(bool)) -> typemacro!(bool) visibility=private
        | -Trait visibility=private
        |  T visibility=none
        | Foo visibility=private
        | -Trait for Foo visibility=none
        |  T visibility=none
    """)

    fun `test macro expanded expressions are always invisible`() = doTest("""
        macro_rules! expr {
            ($ b: expr) => {if $ b {
                struct Foo;

                2
            } else {
                pub trait Bar {}

                3
            }};
        }

        fn f(b: bool) {
            let _x = expr!(b);
        }

        const C: u32 = {
            expr!(true)
        };

        type Ty = [u8; expr!(false)];
    """, """
        |-main.rs visibility=none
        | expr visibility=none
        | f(bool) visibility=private
        | C: u32 visibility=private
        | Ty visibility=private
    """, """
        |-main.rs visibility=none
        | expr visibility=none
        | f(bool) visibility=private
        | C: u32 visibility=private
        | Ty visibility=private
    """)

    override val actionId: String = RsMacroExpandedFilter.ID
}
