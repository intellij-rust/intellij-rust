/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace.Edition

class RsAwaitCompletionTest : RsCompletionTestBase() {

    @MockEdition(Edition.EDITION_2015)
    fun `test postfix await 2015 (anon)`() = checkNotContainsCompletion("await", """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        fn foo() -> impl Future<Output=i32> { unimplemented!() }
        fn main() {
            foo()./*caret*/;
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test postfix await 2015 (adt)`() = checkNotContainsCompletion("await", """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        struct S;
        impl Future for S { type Output = i32; }
        fn foo() -> S { unimplemented!() }
        fn main() {
            foo()./*caret*/;
        }
    """)

    fun `test postfix await 2018 (anon)`() = checkCompletion("await", """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        fn foo() -> impl Future<Output=i32> { unimplemented!() }
        fn main() {
            foo()./*caret*/;
        }
    """, """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        fn foo() -> impl Future<Output=i32> { unimplemented!() }
        fn main() {
            foo().await/*caret*/;
        }
    """)

    fun `test postfix await 2018 (adt)`() = checkCompletion("await", """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        struct S;
        impl Future for S { type Output = i32; }
        fn foo() -> S { unimplemented!() }
        fn main() {
            foo()./*caret*/;
        }
    """, """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }
        struct S;
        impl Future for S { type Output = i32; }
        fn foo() -> S { unimplemented!() }
        fn main() {
            foo().await/*caret*/;
        }
    """)
}
