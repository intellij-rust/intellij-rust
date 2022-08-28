/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class MakeAsyncFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test await inside non-async function`() = checkFixByText("Make function async", """
        pub fn func() {
            x.<error descr="`await` is only allowed inside `async` functions and blocks [E0728]">await/*caret*/</error>;
        }
    """, """
        pub async fn func() {
            x.await;
        }
    """)

    fun `test await inside non-async lambda`() = checkFixByText("Make lambda async", """
        fn func() {
            || {
                x.<error descr="`await` is only allowed inside `async` functions and blocks [E0728]">await/*caret*/</error>;
            };
        }
    """, """
        fn func() {
            async || {
                x.await;
            };
        }
    """)

    fun `test await inside async function`() = checkFixIsUnavailable("Make function async", """
        async fn func() {
            x.await/*caret*/;
        }
    """)

    fun `test await inside async lambda`() = checkFixIsUnavailable("Make lambda async", """
        fn func() {
            async || {
                x.await/*caret*/;
            }
        }
    """)

    fun `test await inside async block`() = checkFixIsUnavailable("Make function async", """
        fn func() {
            async {
                x.await/*caret*/;
            };
        }
    """)
}
