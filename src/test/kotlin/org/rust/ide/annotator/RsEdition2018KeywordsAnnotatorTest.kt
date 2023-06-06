/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockEdition
import org.rust.SkipTestWrapping
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.colors.RsColor

@SkipTestWrapping
class RsEdition2018KeywordsAnnotatorTest : RsAnnotatorTestBase(RsEdition2018KeywordsAnnotator::class) {

    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(RsColor.KEYWORD.testSeverity))
    }

    @MockEdition(Edition.EDITION_2015)
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
    fun `test reserved keywords in macro names in edition 2018`() = checkErrors("""
        fn main() {
            let x = async!();
            let y = await!(x);
            let z = try!(());
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test async in edition 2015`() = checkErrors("""
        <error descr="This feature is only available in Edition 2018">async</error> fn foo() {}

        fn main() {
            <error descr="This feature is only available in Edition 2018">async</error> { () };
            <error descr="This feature is only available in Edition 2018">async</error> || { () };
            <error descr="This feature is only available in Edition 2018">async</error> move || { () };
        }
    """)

    fun `test async in edition 2018`() = checkErrors("""
        <KEYWORD>async</KEYWORD> fn foo() {}

        fn main() {
            <KEYWORD>async</KEYWORD> { () };
            <KEYWORD>async</KEYWORD> || { () };
            <KEYWORD>async</KEYWORD> move || { () };
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test try in edition 2015`() = checkErrors("""
        fn main() {
            <error descr="This feature is only available in Edition 2018">try</error> { () };
        }
    """)

    fun `test try in edition 2018`() = checkErrors("""
        fn main() {
            <KEYWORD>try</KEYWORD> { () };
        }
    """)

    fun `test don't analyze macro def-call bodies, attributes and use items`() = checkErrors("""
        use dummy::async;
        use dummy::await;
        use dummy::{async, await};

        macro_rules! foo {
            () => { async };
        }

        #[<error descr="`async` is reserved keyword in Edition 2018">async</error>]
        fn foo1() {
            #![<error descr="`async` is reserved keyword in Edition 2018">async</error>]
        }

        #[foo::<error descr="`async` is reserved keyword in Edition 2018">async</error>]
        fn foo2() {
            #![foo::<error descr="`async` is reserved keyword in Edition 2018">async</error>]
        }

        #[bar(async)]
        fn foo3() {
            #![bar(async)]
        }

        fn main() {
            foo!(async);
        }
    """)

    fun `test await postfix syntax`() = checkErrors("""
        fn main() {
            let x = f().await;
            let y = f().<error descr="`await` is reserved keyword in Edition 2018">await</error>();
        }
    """)

    @BatchMode
    fun `test no keyword highlighting in batch mode`() = checkHighlighting("""
        async fn foo() {}
        fn main() {
            try { () };
            let x = foo().await;
        }
    """, ignoreExtraHighlighting = false)
}
