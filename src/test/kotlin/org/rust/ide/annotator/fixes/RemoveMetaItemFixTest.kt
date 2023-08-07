/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockAdditionalCfgOptions
import org.rust.MockRustcVersion
import org.rust.SkipTestWrapping
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsAttrErrorAnnotator

@SkipTestWrapping // TODO RsAttrErrorAnnotator in macros
class RemoveMetaItemFixTest : RsAnnotatorTestBase(RsAttrErrorAnnotator::class) {

    @MockRustcVersion("1.0.0-nightly")
    fun `test single item`() = checkFixByText("Remove feature `managed_boxes`", """
        #![feature(<error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes/*caret*/</error>)]
        fn main() {}
    """, """
        fn main() {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test two items left`() = checkFixByText("Remove feature `managed_boxes`", """
        #![feature(<error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes/*caret*/</error>, <error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes</error>)]
        fn main() {}
    """, """
        #![feature(managed_boxes)]
        fn main() {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test two items right`() = checkFixByText("Remove feature `managed_boxes`", """
        #![feature(<error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes</error>, <error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes/*caret*/</error>)]
        fn main() {}
    """, """
        #![feature(managed_boxes)]
        fn main() {}
    """)

    @MockRustcVersion("0.9.0-nightly")
    fun `test feature is not removed`() = checkFixIsUnavailable("Remove feature `managed_boxes`", """
        #![feature(managed_boxes/*caret*/)]
        fn main() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockRustcVersion("1.0.0-nightly")
    fun `test feature inside cfg_attr`() = checkFixIsUnavailable("Remove feature `managed_boxes`", """
        #![cfg_attr(intellij_rust, feature(<error descr="Feature `managed_boxes` has been removed [E0557]">managed_boxes/*caret*/</error>))]
        fn main() {}
    """)
}
