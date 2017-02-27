package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase

class AddMutableTest : RsAnnotatorTestBase() {
    fun `test fix let at reassign`() = checkQuickFix("Make variable mutable", """
fn main() {
    let test = 10;
    test/*caret*/ = 5;
}
""", """
fn main() {
    let mut test = 10;
    test = 5;
}
""")

    fun `test fix let pattern at reassign`() = checkQuickFix("Make variable mutable", """
fn main() {
    let (test, test2) = (10, 20);
    test/*caret*/ = 5;
}
""", """
fn main() {
    let (mut test, test2) = (10, 20);
    test = 5;
}
""")

    fun `test fix let pattern at reassign 2`() = checkQuickFix("Make variable mutable", """
fn main() {
    let (test, test2) = (10, 20);
    test2/*caret*/ = 5;
}
""", """
fn main() {
    let (test, mut test2) = (10, 20);
    test2 = 5;
}
""")

    fun `test fix let at method call (self)`() = checkQuickFix("Make variable mutable", """
struct S;

impl S {
    fn test(&mut self) {
        unimplemented!();
    }
}

fn main() {
    let test = S;
    test/*caret*/.test();
}
""", """
struct S;

impl S {
    fn test(&mut self) {
        unimplemented!();
    }
}

fn main() {
    let mut test = S;
    test.test();
}
""")

    fun `test fix let at method call (args)`() = checkQuickFix("Make variable mutable", """
struct S;

impl S {
    fn test(&self, test: &mut S) {
        unimplemented!();
    }
}

fn main() {
    let test = S;
    let reassign = S;
    test.test(&mut reassign/*caret*/);
}
""", """
struct S;

impl S {
    fn test(&self, test: &mut S) {
        unimplemented!();
    }
}

fn main() {
    let test = S;
    let mut reassign = S;
    test.test(&mut reassign);
}
""")

    fun `test fix let at call (args)`() = checkQuickFix("Make variable mutable", """
struct S;

fn test(test: &mut S) {
    unimplemented!();
}

fn main() {
    let s = S;
    test(&mut s/*caret*/);
}
""", """
struct S;

fn test(test: &mut S) {
    unimplemented!();
}

fn main() {
    let mut s = S;
    test(&mut s);
}
""")

    fun `test fix method at call (args)`() = checkQuickFix("Make variable mutable", """
fn test(test: i32) {
    test/*caret*/ = 32
}
""", """
fn test(mut test: i32) {
    test = 32
}
""")

    fun `test fix method at method call (self)`() = checkQuickFix("Make variable mutable", """
struct S;

impl S {
    fn test(&self) {
        self/*caret*/.foo();
    }
    fn foo(&mut self) {
        unimplemented!();
    }
}
""", """
struct S;

impl S {
    fn test(&mut self) {
        self.foo();
    }
    fn foo(&mut self) {
        unimplemented!();
    }
}
""")

    fun `test fix method at method call (args)`() = checkQuickFix("Make variable mutable", """
struct S;

impl S {
    fn test(&self, s: &S) {
        s/*caret*/.foo();
    }
    fn foo(&mut self) {
        unimplemented!();
    }
}
""", """
struct S;

impl S {
    fn test(&self, s: &mut S) {
        s.foo();
    }
    fn foo(&mut self) {
        unimplemented!();
    }
}
""")
}
