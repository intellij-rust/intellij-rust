/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockRustcVersion
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotationTestBase

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class ImplementFromTraitFixTest : RsAnnotationTestBase() {
    fun `test non available`() = checkFixIsUnavailable(
        "Create 'From<AError>' for type 'BError'", """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A; struct AError;
struct B; struct BError;

impl From<AError> for BError {
    fn from(_: A) -> Self {
        unimplemented!()
    }
}

impl ops::Try for B{
    type Ok = ();
    type Error = BError;

    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}
impl ops::Try for A{
    type Ok = ();
    type Error = AError;


    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}

fn foo()->A{A{}}
fn bar()->B{foo()?<caret>; B{}}
fn main(){
    bar();
}


        """
    )

    @MockRustcVersion("1.29.0")
    fun `test non available in stable`() = checkFixIsUnavailable(
        "Create 'From<AError>' for type 'BError'", """
use std::ops;
use std::ops::Try;
struct A; struct AError;
struct B; struct BError;



impl ops::Try for B{
    type Ok = ();
    type Error = BError;

    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}
impl ops::Try for A{
    type Ok = ();
    type Error = AError;


    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}

fn foo()->A{A{}}
fn bar()->B{foo()?<caret>; B{}}
fn main(){
    bar();
}


        """
    )

    @MockRustcVersion("1.29.0-nightly")
    fun testSimple() = checkFixByText("Create 'From<AError>' for type 'BError'", """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A; struct AError;
struct B; struct BError;


impl ops::Try for B{
    type Ok = ();
    type Error = BError;

    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}
impl ops::Try for A{
    type Ok = ();
    type Error = AError;


    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}

fn foo()->A{A{}}
fn bar()->B{foo()<error descr="the trait `std::convert::From<AError>` is not implemented for `BError`">?<caret></error>; B{}}
fn main(){
    bar();
}
    """,
        """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A; struct AError;
struct B; struct BError;

impl From<AError> for BError {
    fn from(_: AError) -> Self {
        unimplemented!()
    }
}


impl ops::Try for B{
    type Ok = ();
    type Error = BError;

    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}
impl ops::Try for A{
    type Ok = ();
    type Error = AError;


    fn into_result(self) -> Result<<Self as Try>::Ok, <Self as Try>::Error> {
        unimplemented!()
    }

    fn from_error(v: <Self as Try>::Error) -> Self {
        unimplemented!()
    }

    fn from_ok(v: <Self as Try>::Ok) -> Self {
        unimplemented!()
    }
}

fn foo()->A{A{}}
fn bar()->B{foo()?<caret>; B{}}
fn main(){
    bar();
}
        """)
}
