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
class ConvertToTyUsingFromStrFixTest : RsInspectionsTestBase(RsTypeCheckInspection()) {
    fun `test A from &str when impl FromStr for A is available`() = simpleTestWithStr("\"HelloWorld!\"")
    fun `test A from &String when impl FromStr for A is available`() = simpleTestWithStr("&String::from(\"HelloWorld!\")", "(&String::from(\"HelloWorld!\"))")
    fun `test A from &mut Str when impl FromStr for A is available`() = simpleTestWithStr("String::from(\"HelloWorld!\").as_mut_str()")
    fun `test A from &mut String when impl FromStr for A is available`() = simpleTestWithStr("&mut String::from(\"HelloWorld!\")", "(&mut String::from(\"HelloWorld!\"))")

    fun `test A from str when impl FromStr for A is available and fn ret Result with Err match`() = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn foo() -> Result<Aa, Ee> {
            let a: Aa = <error>"Hello World!"<caret></error>;
            return Ok(a);
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn foo() -> Result<Aa, Ee> {
            let a: Aa = "Hello World!".parse()?;
            return Ok(a);
        }
    """)

    fun `test A from str when impl FromStr for A is available and fn ret Result with Err mismatch`() = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn foo() -> Result<Aa, Ee2> {
            let a: Aa = <error>"Hello World!"<caret></error>;
            return Ok(a);
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn foo() -> Result<Aa, Ee2> {
            let a: Aa = "Hello World!".parse().unwrap();
            return Ok(a);
        }
    """)

    fun `test A from str when impl FromStr for A is available and fn ret Result with Err match through From trait`() = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }
        impl From<Ee> for Ee2 { fn from(e: Ee) -> Self {Ee2} }

        fn foo() -> Result<Aa, Ee2> {
            let a: Aa = <error>"Hello World!"<caret></error>;
            return Ok(a);
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }
        impl From<Ee> for Ee2 { fn from(e: Ee) -> Self {Ee2} }

        fn foo() -> Result<Aa, Ee2> {
            let a: Aa = "Hello World!".parse()?;
            return Ok(a);
        }
    """)

    fun `test A from str when impl FromStr for A is available and lambda ret Result with Err match`() = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main() {
            let f = || -> Result<Aa, Ee> { let a: Aa = <error>"Hello World!"<caret></error>; return Ok(a);};
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main() {
            let f = || -> Result<Aa, Ee> { let a: Aa = "Hello World!".parse()?; return Ok(a);};
        }
    """)

    fun `test Result of A from str when impl FromStr for A is available`() = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main () {
            let a: Result<Aa, Ee> = <error>"Hello World!"<caret></error>;
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main () {
            let a: Result<Aa, Ee> = "Hello World!".parse();
        }
    """)

    fun `test no fix when impl FromStr for A has wrong Err type`() = checkFixIsUnavailable("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;
        #[derive(Debug)] struct Ee2;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main () {
            let a: Result<Aa, Ee2> = <error>"Hello World!"<caret></error>;
        }
    """)

    fun `test no fix when impl FromStr for A is not available`() = checkFixIsUnavailable("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        struct Bb;
        #[derive(Debug)] struct Ee;

        impl FromStr for Bb { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Bb)} }
        fn main () {
            let a: Aa = <error>"Hello World!"<caret></error>;
        }
    """)

    private fun simpleTestWithStr(str: String, expStr: String=str)  = checkFixByText("Convert to Aa using `FromStr` trait", """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main () {
            let a: Aa = <error>$str<caret></error>;
        }
    """, """
        use std::str::FromStr;

        struct Aa;
        #[derive(Debug)] struct Ee;

        impl FromStr for Aa { type Err = Ee; fn from_str(s: &str) -> Result<Self, Self::Err> {Ok(Aa)} }

        fn main () {
            let a: Aa = $expStr.parse().unwrap();
        }
    """)
}
