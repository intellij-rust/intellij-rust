/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

class RsEdition2018KeywordsAnnotatorTest : RsAnnotatorTestBase(RsEdition2018KeywordsAnnotator::class.java) {

    fun `test edition 2018 keywords in edition 2015`() = checkErrors("""
        fn main() {
            let async = ();
            let await = ();
            let try = ();
            let x = async;
            let y = await;
            let z = try;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test edition 2018 keywords in edition 2018`() = checkErrors("""
        fn main() {
            let <error descr="`async` is reserved keyword in Edition 2018">async</error> = ();
            let <error descr="`await` is reserved keyword in Edition 2018">await</error> = ();
            let <error descr="`try` is reserved keyword in Edition 2018">try</error> = ();
            let x = <error descr="`async` is reserved keyword in Edition 2018">async</error>;
            let y = <error descr="`await` is reserved keyword in Edition 2018">await</error>;
            let z = <error descr="`try` is reserved keyword in Edition 2018">try</error>;
        }
    """)

    // We should report an error here
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test reserved keywords in macro names in edition 2018`() = checkErrors("""
        fn main() {
            let x = async!();
            let y = await!(x);
            let z = try!(());
        }
    """)

    fun `test async in edition 2015`() = checkErrors("""
        <error descr="This feature is only available in Edition 2018">async</error> fn foo() {}

        fn main() {
            <error descr="This feature is only available in Edition 2018">async</error> { () };
            <error descr="This feature is only available in Edition 2018">async</error> || { () };
            <error descr="This feature is only available in Edition 2018">async</error> move || { () };
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test async in edition 2018`() = checkErrors("""
        async fn foo() {}

        fn main() {
            async { () };
            async || { () };
            async move || { () };
        }
    """)

    fun `test try in edition 2015`() = checkErrors("""
        fn main() {
            <error descr="This feature is only available in Edition 2018">try</error> { () };
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test try in edition 2018`() = checkErrors("""
        fn main() {
            try { () };
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test don't analyze macro def-call bodies and use items`() = checkErrors("""
        use dummy::async;
        use dummy::await;
        use dummy::{async, await};

        macro_rules! foo {
            () => { async };
        }

        fn main() {
            foo!(async)
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test await postfix syntax`() = checkErrors("""
        fn main() {
            let x = f().await;
            let y = f().<error descr="`await` is reserved keyword in Edition 2018">await</error>();
        }
    """)
}
