/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsDocLinkResolveTest : RsResolveTestBase() {

    fun `test unqualified path`() = checkByCode("""
        /// [link]: bar
                  //^
        fn foo() {}
        fn bar() {}
         //X
    """)

    fun `test qualified path`() = checkByCode("""
        /// [link]: inner::bar
                         //^
        fn foo() {}
        mod inner {
            pub fn bar() {}
        }        //X
    """)

    fun `test absolute path`() = checkByCode("""
        mod inner {
            /// [link]: crate::bar
                             //^
            fn foo() {}
        }
        fn bar() {}
         //X
    """)
}
