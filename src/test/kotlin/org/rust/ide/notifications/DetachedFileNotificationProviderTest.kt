/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import org.rust.ExpandMacros
import org.rust.fileTree

class DetachedFileNotificationProviderTest : RsNotificationProviderTestBase() {

    override val notificationProvider: RsNotificationProvider get() = DetachedFileNotificationProvider(project)

    override fun setUp() {
        super.setUp()
        fileTree {
            rust("main.rs", """
                macro_rules! generate_include {
                    (${'$'}package: tt) => {
                        include!(${'$'}package);
                    };
                }

                include!("bar.rs");
                generate_include!("qqq.rs");
                mod foo;
                fn main() {}
            """)
            rust("foo.rs", "")
            rust("bar.rs", "")
            rust("baz.rs", "")
            rust("qqq.rs", "")
        }.create()
    }

    fun `test no notification for attached root file`() = doTest("main.rs")
    fun `test no notification for attached module file`() = doTest("foo.rs")
    fun `test notification for included file`() = doTest("bar.rs")
    @ExpandMacros
    fun `test notification for included file via macro call`() = doTest("qqq.rs")
    fun `test notification for detached file`() = doTest("baz.rs", DetachedFileNotificationProvider.DETACHED_FILE)
}
