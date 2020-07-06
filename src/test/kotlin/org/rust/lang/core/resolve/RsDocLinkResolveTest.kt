/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsDocLinkResolveTest : RsResolveTestBase() {
    fun test() = checkByCode("""
        fn foo() {
            struct Foo;
                  //X
            /// [Foo](test_package/struct.Foo.html)
                                         //^
            fn foo() {}
        }
    """)
}
