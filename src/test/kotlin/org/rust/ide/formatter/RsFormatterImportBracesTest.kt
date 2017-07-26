/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsFormatterImportBracesTest : RsFormatterTestBase() {

    fun testRemoveBracesIfSingleImport() = doTextTest("use getopts::{optopt};", "use getopts::optopt;")

    fun testWontRemoveBracesIfMultiImport() = doTextTest("use getopts::{optopt, optarg};",
        "use getopts::{optopt, optarg};")

    fun `test won't remove braces for single self`() = doTextTest("use getopts::{self};",
        "use getopts::{self};")

    fun testRemoveBracesWithMultipleImports() = doTextTest(
        """
        use getopts::{optopt};
        use std::io::{self, Read, Write};
        use std::Vec::{Vec};
        """,
        """
        use getopts::optopt;
        use std::io::{self, Read, Write};
        use std::Vec::Vec;
        """)
}
