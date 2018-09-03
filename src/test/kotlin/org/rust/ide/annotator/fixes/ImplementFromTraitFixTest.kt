/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.testFramework.LightProjectDescriptor
import org.rust.MockRustcVersion
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotationTestBase

class ImplementFromTraitFixTest : RsAnnotationTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun `test non available`() = checkFixIsUnavailable(
        "Create 'From<A>' for type 'B'", """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A{}
struct B{}

impl From<A> for B {
    fn from(_: A) -> Self {
        unimplemented!()
    }
}

impl ops::Try for B{
    type Ok = ();
    type Error = B;

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
    type Error = A;


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
        "Create 'From<A>' for type 'B'", """
use std::ops;
use std::ops::Try;
struct A{}
struct B{}



impl ops::Try for B{
    type Ok = ();
    type Error = B;

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
    type Error = A;


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
    fun testSimple() = checkFixByText("Create 'From<A>' for type 'B'", """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A{}
struct B{}


impl ops::Try for B{
    type Ok = ();
    type Error = B;

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
    type Error = A;


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
fn bar()->B{foo()<error descr="the trait `std::convert::From<A>` is not implemented for `B`">?<caret></error>; B{}}
fn main(){
    bar();
}
    """,
        """
#![feature(try_trait)]
use std::ops;
use std::ops::Try;
struct A{}
struct B{}

impl From<A> for B {
    fn from(_: A) -> Self {
        unimplemented!()
    }
}


impl ops::Try for B{
    type Ok = ();
    type Error = B;

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
    type Error = A;


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
