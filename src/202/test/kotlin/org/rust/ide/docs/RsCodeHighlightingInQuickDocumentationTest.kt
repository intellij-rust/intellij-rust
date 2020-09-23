/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsCodeHighlightingInQuickDocumentationTest : RsDocumentationProviderTest() {

    fun `test code highlighting`() = doTest("""
        /// Adds one to the number given.
        ///
        /// # Examples
        ///
        /// ```
        /// let five = 5;
        ///
        /// assert_eq!(6, add_one(5));
        /// ```
        fn add_one(x: i32) -> i32 {
            x + 1
        }

        fn main() {
            add_one(91)
             //^
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>add_one</b>(x: i32) -&gt; i32</pre></div>
        <div class='content'><p>Adds one to the number given.</p><h2>Examples</h2><pre style="..."><span style="...">let </span>five = <span style="...">5</span>;

        assert_eq!(<span style="...">6</span>, add_one(<span style="...">5</span>));
        </pre>
        </div>
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/495
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test issue495`() = doTest("""
        /// A cheap, reference-to-reference conversion.
        ///
        /// `AsRef` is very similar to, but different than, `Borrow`. See
        /// [the book][book] for more.
        ///
        /// [book]: ../../book/borrow-and-asref.html
        ///
        /// **Note: this trait must not fail**. If the conversion can fail, use a dedicated method which
        /// returns an `Option<T>` or a `Result<T, E>`.
        ///
        /// # Examples
        ///
        /// Both `String` and `&str` implement `AsRef<str>`:
        ///
        /// ```
        /// fn is_hello<T: AsRef<str>>(s: T) {
        ///    assert_eq!("hello", s.as_ref());
        /// }
        ///
        /// let s = "hello";
        /// is_hello(s);
        ///
        /// let s = "hello".to_string();
        /// is_hello(s);
        /// ```
        ///
        /// # Generic Impls
        ///
        /// - `AsRef` auto-dereference if the inner type is a reference or a mutable
        /// reference (eg: `foo.as_ref()` will work the same if `foo` has type `&mut Foo` or `&&mut Foo`)
        ///
        #[stable(feature = "rust1", since = "1.0.0")]
        pub trait AsRef<T: ?Sized> {
            /// Performs the conversion.
            #[stable(feature = "rust1", since = "1.0.0")]
            fn as_ref(&self) -> &T;
        }

        impl <T> AsRef<T> {}
                  //^
    """, """
        <div class='definition'><pre>test_package
        pub trait <b>AsRef</b>&lt;T: ?<a href="psi_element://Sized">Sized</a>&gt;</pre></div>
        <div class='content'><p>A cheap, reference-to-reference conversion.</p><p><code>AsRef</code> is very similar to, but different than, <code>Borrow</code>. See
        <a href="psi_element://../book/borrow-and-asref.html">the book</a> for more.</p><p><strong>Note: this trait must not fail</strong>. If the conversion can fail, use a dedicated method which
        returns an <code>Option&lt;T&gt;</code> or a <code>Result&lt;T, E&gt;</code>.</p><h2>Examples</h2><p>Both <code>String</code> and <code>&amp;str</code> implement <code>AsRef&lt;str&gt;</code>:</p><pre style="..."><span style="...">fn </span>is_hello&lt;T: AsRef&lt;str&gt;&gt;(s: T) {
           assert_eq!(<span style="...">&quot;hello&quot;</span>, s.as_ref());
        }

        <span style="...">let </span>s = <span style="...">&quot;hello&quot;</span>;
        is_hello(s);

        <span style="...">let </span>s = <span style="...">&quot;hello&quot;</span>.to_string();
        is_hello(s);
        </pre>
        <h2>Generic Impls</h2><ul><li><code>AsRef</code> auto-dereference if the inner type is a reference or a mutable
        reference (eg: <code>foo.as_ref()</code> will work the same if <code>foo</code> has type <code>&amp;mut Foo</code> or <code>&amp;&amp;mut Foo</code>)</li></ul></div>
    """)

    fun `test process code lines start with #`() = doTest("""
        /// Some doc.
        ///
        /// # Example
        ///
        /// ```
        /// #[attr1]
        /// ##[attr2]
        ///    ##[attr3]
        /// #
        ///   #
        /// let foo = Foo;
        /// # let foo2 = Foo;
        ///    # let foo3 = Foo;
        ///    let foo4 = Foo;
        /// ```
        struct Foo;
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b></pre></div>
        <div class='content'><p>Some doc.</p><h2>Example</h2><pre style="...">#[attr1]
        #[attr2]
        #[attr3]
        <span style="...">let </span>foo = Foo;
           <span style="...">let </span>foo4 = Foo;
        </pre>
        </div>
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Html") expected: String)
        = doTest(code, expected) { element, context -> generateDoc(element, context)?.hideSpecificStyles() }
}
