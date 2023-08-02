/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class SlicePostfixTemplateTestBase(private val key: String) :
    RsPostfixTemplateTest(SlicePostfixTemplate::class, key) {

    fun `test non index expr`() = doTestNotApplicable("""
            let v = 0;
            v.$key/*caret*/
    """)

    fun `test vec`() = doTest("""
        fn main() {
            let v = vec![1, 2, 3];
            v.$key/*caret*/
        }
    """, """
        fn main() {
            let v = vec![1, 2, 3];
            &v[i..j]/*caret*/
        }
    """)

    fun `test array`() = doTest("""
        fn main() {
            let v = [1, 2, 3];
            v.$key/*caret*/
        }
    """, """
        fn main() {
            let v = [1, 2, 3];
            &v[i..j]/*caret*/
        }
    """)

    fun `test str`() = doTest("""
        fn main() {
            let v = "123";
            v.$key/*caret*/
        }
    """, """
        fn main() {
            let v = "123";
            &v[i..j]/*caret*/
        }
    """)

    fun `test slice`() = doTest("""
        fn main() {
            let v = vec![1, 2, 3];
            let vs = &v[0..2];
            vs.$key/*caret*/
        }
    """, """
        fn main() {
            let v = vec![1, 2, 3];
            let vs = &v[0..2];
            &vs[i..j]/*caret*/
        }
    """)

    fun `test refs`() = doTest("""
        fn main() {
            let v = "123";
            let vrrr = &&&v;
            vrrr.$key/*caret*/
        }
    """, """
        fn main() {
            let v = "123";
            let vrrr = &&&v;
            &vrrr[i..j]/*caret*/
        }
    """)

    fun `test indexable 1`() = doTest("""
        use std::ops::{Index, Range};
        struct S;
        impl Index<Range<usize>> for S {
            type Output = ();
            fn index(&self, index: Range<usize>) -> &Self::Output { todo!() }
        }
        fn main() {
            let v = S;
            v.$key/*caret*/
        }
    """, """
        use std::ops::{Index, Range};
        struct S;
        impl Index<Range<usize>> for S {
            type Output = ();
            fn index(&self, index: Range<usize>) -> &Self::Output { todo!() }
        }
        fn main() {
            let v = S;
            &v[i..j]/*caret*/
        }
    """)

    fun `test indexable 2`() = doTestNotApplicable("""
        use std::ops::{Index, Range};
        struct S;
        impl Index<usize> for S {
            type Output = ();
            fn index(&self, index: usize) -> &Self::Output { todo!() }
        }
        fn main() {
            let v = S;
            v.$key/*caret*/
        }
    """)
}

class SlicePostfixTemplateTest : SlicePostfixTemplateTestBase("slice")
class SublistPostfixTemplateTest : SlicePostfixTemplateTestBase("sublist")
