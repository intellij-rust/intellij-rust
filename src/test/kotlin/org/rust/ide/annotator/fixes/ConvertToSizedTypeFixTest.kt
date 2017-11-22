/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase

class ConvertToSizedTypeFixTest : RsAnnotatorTestBase() {

    fun `test convert function arg to reference`() = checkQuickFix("Convert to reference", """
        fn foo(slice: [u8]/*caret*/) {}
    """, """
        fn foo(slice: &[u8]) {}
    """)

    fun `test convert function return type to reference`() = checkQuickFix("Convert to reference", """
        fn foo() -> [u8]/*caret*/ { unimplemented!() }
    """, """
        fn foo() -> &[u8] { unimplemented!() }
    """)

    fun `test convert function arg to Box`() = checkQuickFix("Convert to Box", """
        trait Foo {}
        fn foo(foo: Foo/*caret*/) {}
    """, """
        trait Foo {}
        fn foo(foo: Box<Foo>) {}
    """)

    fun `test convert function return type to Box`() = checkQuickFix("Convert to Box", """
        trait Foo {}
        fn foo() -> Foo/*caret*/ { unimplemented!() }
    """, """
        trait Foo {}
        fn foo() -> Box<Foo> { unimplemented!() }
    """)
}
