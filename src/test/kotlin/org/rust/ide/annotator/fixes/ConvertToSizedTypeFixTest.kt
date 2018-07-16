/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotationTestBase

class ConvertToSizedTypeFixTest : RsAnnotationTestBase() {

    fun `test convert function arg to reference`() = checkFixByText("Convert to reference", """
        fn foo(slice: <error>[u8]/*caret*/</error>) {}
    """, """
        fn foo(slice: &[u8]) {}
    """)

    fun `test convert function return type to reference`() = checkFixByText("Convert to reference", """
        fn foo() -> <error>[u8]/*caret*/</error> { unimplemented!() }
    """, """
        fn foo() -> &[u8] { unimplemented!() }
    """)

    fun `test convert function arg to Box`() = checkFixByText("Convert to Box", """
        trait Foo {}
        fn foo(foo: <error>Foo/*caret*/</error>) {}
    """, """
        trait Foo {}
        fn foo(foo: Box<Foo>) {}
    """)

    fun `test convert function return type to Box`() = checkFixByText("Convert to Box", """
        trait Foo {}
        fn foo() -> <error>Foo/*caret*/</error> { unimplemented!() }
    """, """
        trait Foo {}
        fn foo() -> Box<Foo> { unimplemented!() }
    """)
}
