/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class IfLetToMatchIntentionTest : RsIntentionTestBase(IfLetToMatchIntention()) {
    fun `test option with some`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(value) = x {/*caret*/
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(value) => {
                    println!("some")
                }
                None => {}
            }
        }
    """)

    fun `test option with refutable some`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(42) = x {/*caret*/
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(42) => {
                    println!("some")
                }
                _ => {}
            }
        }
    """)

    fun `test option with wild some`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(_) = x {/*caret*/
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(_) => {
                    println!("some")
                }
                None => {}
            }
        }
    """)

    fun `test option with range`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(1..=5) = x {/*caret*/
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(1..=5) => {
                    println!("some")
                }
                _ => {}
            }
        }
    """)

    fun `test option with none`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let None = x {/*caret*/
                println!("none")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                None => {
                    println!("none")
                }
                Some(..) => {}
            }
        }
    """)

    fun `test option with unnecessary parentheses around pattern`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let (((None))) = x {/*caret*/
                println!("none")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                (((None))) => {
                    println!("none")
                }
                Some(..) => {}
            }
        }
    """)

    fun `test option full 1`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(value) = x {/*caret*/
                println!("some")
            } else {
                println!("none")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(value) => {
                    println!("some")
                }
                None => {
                    println!("none")
                }
            }
        }
    """)

    fun `test option full 2`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let None = x {/*caret*/
                println!("none")
            } else {
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                None => {
                    println!("none")
                }
                Some(..) => {
                    println!("some")
                }
            }
        }
    """)

    fun `test option full with unnecessary parentheses around pattern`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let ((((None)))) = x {/*caret*/
                println!("none")
            } else if let Some(a) = x {
                println!("some")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                ((((None)))) => {
                    println!("none")
                }
                Some(a) => {
                    println!("some")
                }
            }
        }
    """)

    fun `test result with ok`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            if let Ok(value) = x {/*caret*/
                println!("ok")
            }
        }
    """, """
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            match x {
                Ok(value) => {
                    println!("ok")
                }
                Err(..) => {}
            }
        }
    """)

    fun `test result with err`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, i32> = Err(42);
            if let Err(e) = x {/*caret*/
                println!("err")
            }
        }
    """, """
        fn main() {
            let x: Result<i32, i32> = Err(42);
            match x {
                Err(e) => {
                    println!("err")
                }
                Ok(..) => {}
            }
        }
    """)

    fun `test result full 1`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            if let Ok(value) = x {/*caret*/
                println!("ok")
            } else {
                println!("err")
            }
        }
    """, """
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            match x {
                Ok(value) => {
                    println!("ok")
                }
                Err(..) => {
                    println!("err")
                }
            }
        }
    """)

    fun `test result full 2`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            if let Err(e) = x {/*caret*/
                println!("err")
            } else {
                println!("ok")
            }
        }
    """, """
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            match x {
                Err(e) => {
                    println!("err")
                }
                Ok(..) => {
                    println!("ok")
                }
            }
        }
    """)

    fun `test simple else`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(val) = x {

            } else {/*caret*/
                println!("it work")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(val) => {}
                None => {
                    println!("it work")
                }
            }
        }
    """)

    fun `test else if`() = doAvailableTest("""
        fn main() {
            if let A(value) = x {

            } else if let B(value) = x {
                /*caret*/
            }
        }
    """, """
        fn main() {
            match x {
                A(value) => {}
                B(value) => {}
                _ => {}
            }
        }
    """)

    fun `test full option else if`() = doAvailableTest("""
        fn main() {
            let x = Some(42);
            if let Some(value) = x {
                println!("some")
            } else if let None = x {
                /*caret*/println!("none")
            }
        }
    """, """
        fn main() {
            let x = Some(42);
            match x {
                Some(value) => {
                    println!("some")
                }
                None => {
                    println!("none")
                }
            }
        }
    """)

    fun `test full result else if`() = doAvailableTest("""
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            if let Ok(value) = x {
                println!("ok")
            } else if let Err(e) = x {
                /*caret*/println!("err")
            }
        }
    """, """
        fn main() {
            let x: Result<i32, i32> = Ok(42);
            match x {
                Ok(value) => {
                    println!("ok")
                }
                Err(e) => {
                    println!("err")
                }
            }
        }
    """)

    fun `test else if else`() = doAvailableTest("""
        fn main() {
            if let A(value) = x {
                /*caret*/
            } else if let B(value) = x {

            } else {

            }
        }
    """, """
        fn main() {
            match x {
                A(value) => {}
                B(value) => {}
                _ => {}
            }
        }
    """)

    fun `test trackback if`() = doAvailableTest("""
        fn main() {
            if let A(value) = x {

            } else if let B(value) = x {
                /*caret*/
            } else {

            }
        }
    """, """
        fn main() {
            match x {
                A(value) => {}
                B(value) => {}
                _ => {}
            }
        }
    """)

    fun `test apply on same target`() = doUnavailableTest("""
        fn main() {
            if let A(value) = x {

            } else if let B(value) = y {
                /*caret*/
            }
        }
    """)

    fun `test available with range`() = doAvailableTest("""
        fn main() {
            let e = 4;
            if let 1..=5 = e {/*caret*/
                println!("got {}", e)
            };
        }
    """, """
        fn main() {
            let e = 4;
            match e {
                1..=5 => {
                    println!("got {}", e)
                }
                _ => {}
            };
        }
    """)

    fun `test available with const`() = doAvailableTest("""
        fn main() {
            let e = 4;
            if let 4 = e {/*caret*/
                println!("got {}", e)
            };
        }
    """, """
        fn main() {
            let e = 4;
            match e {
                4 => {
                    println!("got {}", e)
                }
                _ => {}
            };
        }
    """)

    fun `test available with struct`() = doAvailableTest("""
        struct Point {
            x: bool,
            y: bool,
        }
        fn main() {
            let point = Point { x: false, y: true };
            if let Point { x: true, .. } = point {/*caret*/
                println!("42")
            }
        }
    """, """
        struct Point {
            x: bool,
            y: bool,
        }
        fn main() {
            let point = Point { x: false, y: true };
            match point {
                Point { x: true, .. } => {
                    println!("42")
                }
                _ => {}
            }
        }
    """)

    fun `test available with struct 2`() = doAvailableTest("""
        struct Point {
            x: bool,
            y: bool,
        }
        fn main() {
            let point = Point { x: false, y: true };
            if let Point { x: true, y: f } = point {/*caret*/
                println!("42")
            }
        }
    """, """
        struct Point {
            x: bool,
            y: bool,
        }
        fn main() {
            let point = Point { x: false, y: true };
            match point {
                Point { x: true, y: f } => {
                    println!("42")
                }
                _ => {}
            }
        }
    """)

    fun `test available pattern with tup`() = doAvailableTest("""
        fn main() {
            let e = Some(32);
            if let (Some(42)) = e {/*caret*/
                println!("got {:?}", a)
            }
        }
    """, """
        fn main() {
            let e = Some(32);
            match e {
                (Some(42)) => {
                    println!("got {:?}", a)
                }
                _ => {}
            }
        }
    """)

    fun `test available pattern with tup 2`() = doAvailableTest("""
        fn main() {
            let e = (42, 50);
            if let (a, 50) = e {/*caret*/
                println!("got {:?}", a)
            }
        }
    """, """
        fn main() {
            let e = (42, 50);
            match e {
                (a, 50) => {
                    println!("got {:?}", a)
                }
                _ => {}
            }
        }
    """)

    fun `test available with slice`() = doAvailableTest("""
        fn main() {
            let x = [1, 2];
            if let [1, ..] = x {/*caret*/
                println!("42")
            }
        }
    """, """
        fn main() {
            let x = [1, 2];
            match x {
                [1, ..] => {
                    println!("42")
                }
                _ => {}
            }
        }
    """)

    fun `test available with box`() = doAvailableTest("""
        fn main() {
            let x = box 42;
            if let box 42 = x {/*caret*/
                println!("42")
            }
        }
    """, """
        fn main() {
            let x = box 42;
            match x {
                box 42 => {
                    println!("42")
                }
                _ => {}
            }
        }
    """)

    fun `test available with ref`() = doAvailableTest("""
        fn main() {
            let x = &42;
            if let &42 = x {/*caret*/
                println!("42")
            }
        }
    """, """
        fn main() {
            let x = &42;
            match x {
                &42 => {
                    println!("42")
                }
                _ => {}
            }
        }
    """)

    fun `test multiple if let pattern`() = doAvailableTest("""
        enum V { V1(i32), V2(i32), V3 }
        fn foo(v: V) {
            if let V1(x) | V2(x) = v/*caret*/ {
                println!("{}", x);
            }
        }
    """, """
        enum V { V1(i32), V2(i32), V3 }
        fn foo(v: V) {
            match v {
                V1(x) | V2(x) => {
                    println!("{}", x);
                }
                _ => {}
            }
        }
    """)

    fun `test multiple if let pattern with leading |`() = doAvailableTest("""
        enum V { V1(i32), V2(i32), V3 }
        fn foo(v: V) {
            if let | V1(x) | V2(x) = v/*caret*/ {
                println!("{}", x);
            }
        }
    """, """
        enum V { V1(i32), V2(i32), V3 }
        fn foo(v: V) {
            match v {
                | V1(x) | V2(x) => {
                    println!("{}", x);
                }
                _ => {}
            }
        }
    """)

    fun `test irrefutable pattern`() = doAvailableTest("""
        struct Id(u32);
        struct S { a: Id, b: u32 }
        fn foo(s: S) {
            if let S { a: Id(ref name), .. } = s/*caret*/ {
                let _x = name;
            }
        }
    """, """
        struct Id(u32);
        struct S { a: Id, b: u32 }
        fn foo(s: S) {
            match s {
                S { a: Id(ref name), .. } => {
                    let _x = name;
                }
            }
        }
    """)

    fun `test irrefutable pattern with else`() = doAvailableTest("""
        struct Id(u32);
        struct S { a: Id, b: u32 }
        fn foo(s: S) {
            if let S { a: Id(ref name), .. } = s/*caret*/ {
                let _x = name;
            }
            else {
                let _y = 0;
            }
        }
    """, """
        struct Id(u32);
        struct S { a: Id, b: u32 }
        fn foo(s: S) {
            match s {
                S { a: Id(ref name), .. } => {
                    let _x = name;
                }
                _ => {
                    let _y = 0;
                }
            }
        }
    """)

    fun `test irrefutable single variant enum`() = doAvailableTest("""
        enum V { V1 }
        fn foo(v: V) {
            if let V::V1 = v/*caret*/ {
                println!("hello");
            }
        }
    """, """
        enum V { V1 }
        fn foo(v: V) {
            match v {
                V::V1 => {
                    println!("hello");
                }
            }
        }
    """)

    fun `test irrefutable struct`() = doAvailableTest("""
        struct S;
        fn foo(s: S) {
            if let S = s/*caret*/ {
                println!("hello");
            }
        }
    """, """
        struct S;
        fn foo(s: S) {
            match s {
                S => {
                    println!("hello");
                }
            }
        }
    """)
}
