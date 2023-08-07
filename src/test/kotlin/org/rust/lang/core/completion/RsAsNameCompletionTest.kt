/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsAsNameCompletionTest : RsCompletionTestBase() {
    fun `test complete as after use item`() = doSingleCompletion("""
        use std::fmt::Result a/*caret*/;
    """, """
        use std::fmt::Result as /*caret*/;
    """)

    fun `test complete as after use item without semicolon`() = doSingleCompletion("""
        use std::fmt::Result a/*caret*/
    """, """
        use std::fmt::Result as /*caret*/
    """)

    fun `test complete as after use item without letters`() = doSingleCompletion("""
        use std::fmt::Result /*caret*/;
    """, """
        use std::fmt::Result as /*caret*/;
    """)

    fun `test complete as inside use item group`() = doSingleCompletion("""
        use std::fmt::{Result a/*caret*/};
    """, """
        use std::fmt::{Result as /*caret*/};
    """)

    fun `test complete as inside use item group without letters`() = doSingleCompletion("""
        use std::fmt::{Result /*caret*/};
    """, """
        use std::fmt::{Result as /*caret*/};
    """)

    fun `test complete as inside unclosed use item group`() = doSingleCompletion("""
        use std::fmt::{Resut /*caret*/
    """, """
        use std::fmt::{Resut as /*caret*/
    """)

    fun `test complete as inside use item group after second identifier`() = doSingleCompletion("""
        use std::fmt::{Result as R, Some a/*caret*/};
    """, """
        use std::fmt::{Result as R, Some as /*caret*/};
    """)

    fun `test complete as inside nested use item group`() = doSingleCompletion("""
        use std::{fmt::{Result /*caret*/ }};
    """, """
        use std::{fmt::{Result as /*caret*/ }};
    """)

    fun `test no completion after use item group`() = checkNotContainsCompletion("as", """
        use std::fmt::{Result}/*caret*/;
    """)

    fun `test no completion inside item group without identifier`() = checkNotContainsCompletion("as", """
        use std::fmt::{a/*caret*/};
    """)

    fun `test no completion inside item group without identifier 2`() = checkNotContainsCompletion("as", """
        use std::fmt::{Result as R, a/*caret*/};
    """)

    fun `test no completion inside item group after wildcard`() = checkNotContainsCompletion("as", """
        use std::fmt::{* a/*caret*/};
    """)

    fun `test no completion after wildcard import`() = checkNotContainsCompletion("as", """
        use std::fmt::* /*caret*/;
    """)

    fun `test no completion after unfinished path`() = checkNotContainsCompletion("as", """
        use std::fmt::/*caret*/;
    """)
}
