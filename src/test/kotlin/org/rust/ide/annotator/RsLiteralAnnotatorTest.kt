/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsLiteralAnnotatorTest : RsAnnotatorTestBase() {
    fun `test char literal length`() = checkErrors("""
        fn main() {
            let ch1 = <error descr="empty char literal">''</error>;
            let ch2 = <error descr="too many characters in char literal">'abrakadabra'</error>;
            let ch3 = <error descr="too many characters in byte literal">b'abrakadabra'</error>;
            let ch4 = 'a';
            let ch5 = b'a';
        }
    """)

    fun `test literal suffixes`() = checkErrors("""
        fn main() {
            let lit1 = <error descr="string literal with a suffix is invalid">"test"u8</error>;
            let lit2 = <error descr="char literal with a suffix is invalid">'c'u8</error>;
            let lit3 = <error descr="invalid suffix 'u8' for float literal; the suffix must be one of: 'f32', 'f64'">1.2u8</error>;
            let lit4 = <error descr="invalid suffix 'f34' for float literal; the suffix must be one of: 'f32', 'f64'">1f34</error>;
            let lit4 = <error descr="invalid suffix 'u96' for integer literal; the suffix must be one of: 'u8', 'i8', 'u16', 'i16', 'u32', 'i32', 'u64', 'i64', 'u128', 'i128', 'isize', 'usize'">1u96</error>;
            let lit5 = 1e30f64;
        }
    """)

    fun `test literal unclosed quotes`() = checkErrors("""
        fn main() {
            let ch1 = <error descr="unclosed char literal">'1</error>;
            let ch2 = <error descr="unclosed byte literal">b'1</error>;
        }
    """)
}

