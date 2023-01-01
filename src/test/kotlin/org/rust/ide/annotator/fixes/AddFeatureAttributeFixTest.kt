/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockEdition
import org.rust.MockRustcVersion
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddFeatureAttributeFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    @MockEdition(Edition.EDITION_2015)
    @MockRustcVersion("1.28.0")
    fun `test add crate_in_paths feature is unavailable`() = checkFixIsUnavailable("Add `crate_in_paths` feature", """
        use <error>crate/*caret*/</error>::foo::Foo;
    """)

    @MockEdition(Edition.EDITION_2015)
    @MockRustcVersion("1.29.0-nightly")
    fun `test add crate_in_paths feature`() = checkFixByText("Add `crate_in_paths` feature", """
        use <error>crate/*caret*/</error>::foo::Foo;
    """, """
        #![feature(crate_in_paths)]

        use crate/*caret*/::foo::Foo;
    """, preview = null)

    @MockRustcVersion("1.56.0-nightly")
    fun `test add feature attr after all feature attributes`() = checkFixByText("Add `let_chains` feature", """
        #![feature(if_let_guard)]

        fn main() {
            let x = Some(1);
            if <error>let Some(_) = x/*caret*/</error> && <error>let Some(_) = x</error> {};
        }
    """, """
        #![feature(if_let_guard)]
        #![feature(let_chains)]

        fn main() {
            let x = Some(1);
            if let Some(_) = x/*caret*/ && let Some(_) = x {};
        }
    """, preview = null)
}
