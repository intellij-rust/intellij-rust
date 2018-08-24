/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection


@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class ConvertToTyUsingTryFromTraitFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {
    fun `test B from A when impl TryFrom A for B is available`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main () {
            let b: Bb = <error>Aa<caret></error>;
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main () {
            let b: Bb = Bb::try_from(Aa).unwrap();
        }
    """)

    fun `test B from A when impl TryFrom A for B is available and fn ret Result with Err match`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn foo() -> Result<B, Ee> {
            let b: Bb = <error>Aa<caret></error>;
            return Ok(b);
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn foo() -> Result<B, Ee> {
            let b: Bb = Bb::try_from(Aa)?;
            return Ok(b);
        }
    """)

    fun `test B from A when impl TryFrom A for B is available and fn ret Result with Err mismatch`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn foo() -> Result<B, Ee2> {
            let b: Bb = <error>Aa<caret></error>;
            return Ok(b);
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn foo() -> Result<B, Ee2> {
            let b: Bb = Bb::try_from(Aa).unwrap();
            return Ok(b);
        }
    """)

    fun `test B from A when impl TryFrom A for B is available and fn ret Result with Err match through From trait`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }
        impl From<Ee> for Ee2 { fn from(e: Ee) -> Self {Ee2} }

        fn foo() -> Result<B, Ee2> {
            let b: Bb = <error>Aa<caret></error>;
            return Ok(b);
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }
        impl From<Ee> for Ee2 { fn from(e: Ee) -> Self {Ee2} }

        fn foo() -> Result<B, Ee2> {
            let b: Bb = Bb::try_from(Aa)?;
            return Ok(b);
        }
    """)

    fun `test B from A when impl TryFrom A for B is available and lambda ret Result with Err match`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main() {
            let f = || -> Result<B, Ee> { let b: Bb = <error>Aa<caret></error>; return Ok(b);};
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main() {
            let f = || -> Result<B, Ee> { let b: Bb = Bb::try_from(Aa)?; return Ok(b);};
        }
    """)

    fun `test Result of B from A when impl TryFrom A for B is available`() = checkFixByText("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main () {
            let b: Result<Bb, Ee> = <error>Aa<caret></error>;
        }
    """, """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main () {
            let b: Result<Bb, Ee> = Bb::try_from(Aa);
        }
    """)

    fun `test no fix when impl TryFrom A for B has wrong Err type`() = checkFixIsUnavailable("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl TryFrom<Aa> for Bb { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Bb)} }

        fn main () {
            let b: Result<Bb, Ee2> = <error>Aa<caret></error>;
        }
    """)

    fun `test no fix when impl TryFrom A for B is not available with simple From impl`() = checkFixIsUnavailable("Convert to Bb using `TryFrom` trait", """
        struct Aa;
        struct Bb;

        impl From<Aa> for Bb {
            fn from(a: Aa) -> Self { Bb }
        }

        fn main () {
            let b: Bb = <error>Aa<caret></error>;
        }
    """)

    fun `test no fix when impl TryFrom A for B is not available`() = checkFixIsUnavailable("Convert to Bb using `TryFrom` trait", """
        #![feature(try_from)]
        use std::convert::TryFrom;
        struct Aa;
        struct Bb;
        struct Cc;
        struct Dd;
        #[derive(Debug)] struct Ee;

        impl TryFrom<Cc> for Bb { type Error = Ee; fn try_from(c: Cc) -> Result<Self, Self::Error> {Ok(Bb)} }
        impl TryFrom<Aa> for Dd { type Error = Ee; fn try_from(a: Aa) -> Result<Self, Self::Error> {Ok(Dd)} }

        fn main () {
            let b: Bb = <error>Aa<caret></error>;
        }
    """)
}
