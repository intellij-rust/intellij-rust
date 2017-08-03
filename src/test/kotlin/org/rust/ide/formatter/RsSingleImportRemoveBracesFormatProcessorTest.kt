/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsSingleImportRemoveBracesFormatProcessorTest : RsFormatterTestBase() {

    fun testRemoveBracesIfSingleImport() = doTextTest("use getopts::{optopt};", "use getopts::optopt;")

    fun testWontRemoveBracesIfMultiImport() = checkNotChanged(
        "use getopts::{optopt, optarg};"
    )

    fun `test won't remove braces for single self`() = checkNotChanged(
        "use getopts::{self};"
    )

    fun testRemoveBracesWithMultipleImports() = doTextTest(
        """
        use getopts::{optopt};
        use std::io::{self, Read, Write};
        use std::Vec::{Vec};
    """, """
        use getopts::optopt;
        use std::io::{self, Read, Write};
        use std::Vec::Vec;
    """)
}
