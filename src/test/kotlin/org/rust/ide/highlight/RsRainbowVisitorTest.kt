/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import org.rust.lang.RsTestBase

class RsRainbowVisitorTest : RsTestBase() {

    fun checkRainbow(code: String, isRainbowOn: Boolean = true, withColor: Boolean = false) {
        myFixture.testRainbow(
            "main.rs",
            code,
            isRainbowOn, withColor)
    }

    fun testPathBinding() = checkRainbow("""
        fn main() {
            let mut <rainbow>test</rainbow> = "";
            <rainbow>test</rainbow> = "";
        }
    """)

    fun testFunctionBinding() = checkRainbow("""
        fn foo() {}
        fn main() {
            foo();
        }
    """, true)

    fun testDifferentColor() = checkRainbow("""
        fn main() {
            let mut <rainbow color='ff000002'>test</rainbow> = "";
            <rainbow color='ff000002'>test</rainbow> = "";
            let mut <rainbow color='ff000003'>test</rainbow> = "";
            <rainbow color='ff000003'>test</rainbow> = "";
        }
    """, withColor = true)

    fun testComplexDifferentColor() = checkRainbow("""
        fn foo(<rainbow color='ff000002'>test</rainbow>: i32) {
            let <rainbow color='ff000004'>x</rainbow> = <rainbow color='ff000002'>test</rainbow> + <rainbow color='ff000002'>test</rainbow>;
            let <rainbow color='ff000001'>y</rainbow> = {
               let <rainbow color='ff000003'>test</rainbow> = <rainbow color='ff000004'>x</rainbow>;
            };
            <rainbow color='ff000002'>test</rainbow>
        }
    """, withColor = true)

}
