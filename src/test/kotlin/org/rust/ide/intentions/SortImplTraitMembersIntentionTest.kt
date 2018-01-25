/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SortImplTraitMembersIntentionTest : RsIntentionTestBase(SortImplTraitMembersIntention()) {

    fun `test different order`() = doAvailableTest("""
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        /*caret*/impl Trait for Struct {
            type T2 = T;
            const ID2: i32 = 2;
            fn test3(&self) -> i32 {
                self.i * 3
            }
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            type T1 = T;
            const ID1: i32 = 1;
        }
    """, """
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        impl Trait for Struct {
            const ID1: i32 = 1;
            const ID2: i32 = 2;
            type T1 = T;
            type T2 = T;
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            fn test3(&self) -> i32 {
                self.i * 3
            }
        }
    """
    )

    fun `test same order`() = doUnavailableTest("""
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        /*caret*/impl Trait for Struct {
            const ID1: i32 = 1;
            const ID2: i32 = 2;
            type T1 = T;
            type T2 = T;
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            fn test3(&self) -> i32 {
                self.i * 3
            }
        }
    """)

}
