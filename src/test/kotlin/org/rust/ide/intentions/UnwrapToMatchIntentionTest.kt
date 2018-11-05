package org.rust.ide.intentions

class UnwrapToMatchIntentionTest: RsIntentionTestBase(UnwrapToMatchIntention()) {

    fun `test base case - caret before brackets`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = match a {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test base case - caret between brackets`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap(/*caret*/);
        }
    """, """
        fn main() {
            let a = match a {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test base case - caret after brackets`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap()/*caret*/;
        }
    """, """
        fn main() {
            let a = match a {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test base case - reduntant whitespaces are ignored`() = doAvailableTest("""
        fn main() {
            let a = a           .

                unwrap()/*caret*/;
        }
    """, """
        fn main() {
            let a = match a {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test chain of dot expessions`() = doAvailableTest("""
        fn main() {
            a.b().c().unwrap/*caret*/().d().e().f;
        }
    """, """
        fn main() {
            match a.b().c() {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }.d().e().f;
        }
        """)

    fun `test unwrap() as method call parameter`() = doAvailableTest("""
        fn main() {
            f(a, b.unwrap/*caret*/(), c)
        }
    """, """
        fn main() {
            f(a, match b {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }, c)
        }
    """)

    fun `test binary expression with unwrap() result`() = doAvailableTest("""
        fn main() {
            let x = x.unwrap/*caret*/() + 42;
        }
    """, """
        fn main() {
            let x = match x {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } + 42;
        }
    """)

    fun `test chain of unwrap()-s - stage 1`() = doAvailableTest("""
        fn main() {
            let x = x.unwrap().unwrap/*caret*/().unwrap();
        }
    """, """
        fn main() {
            let x = match x.unwrap() {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }.unwrap();
        }
    """)

    fun `test chain of unwrap()-s - stage 2`() = doAvailableTest("""
        fn main() {
            let x = match x.unwrap/*caret*/() {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }.unwrap();
        }
    """, """
        fn main() {
            let x = match match x {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }.unwrap();
        }
    """)

    fun `test chain of unwrap()-s - stage 3`() = doAvailableTest("""
        fn main() {
            let x = match match x {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            }.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let x = match match match x {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            } {
                Ok(x) => x,
                Err(_) => unimplemented!(),
            };
        }
    """)

    fun `test base case - brackets missing`() = doUnavailableTest("""
         fn main() {
            let a = a.unwrap/*caret*/;
        }
    """)

    fun `test base case - incorrect method call`() = doUnavailableTest("""
         fn main() {
            let a = a.unwra/*caret*/();
        }
    """)

    fun `test base case - single unwrap() call`() = doUnavailableTest("""
         fn main() {
            let a = unwrap/*caret*/();
        }
    """)

    fun `base case - unwrap() call with parameters`() = doUnavailableTest("""
         fn main() {
            let a = unwrap/*caret*/(0);
        }
    """)

    fun `test base case - call with blank type specialization`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<>/*caret*/();
        }
    """)

    fun `test base case - call with non-blank type specialization`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<i32>/*caret*/();
        }
    """)
}
