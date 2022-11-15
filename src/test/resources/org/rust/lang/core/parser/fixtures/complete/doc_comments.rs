/*!
 * inner docs
 * starred line
   not starred line
 */

/// This is Setext level 1 header!
/// ==============================
///
/// This is Setext level 2 header!
/// ------------------------------
///
/// # Header 1
/// ## Header 2
/// ### Header 3
/// #### Header 4
/// ##### Header 5
/// ###### Header 6
/// ####### Not a header
/// #not_a_header
/// #	there is a tab here
/// \## not a header
///  # Header 1 (offset 1)
///   # Header 1 (offset 2)
///    # Header 1 (offset 3)
///
/// Some text.
/// Some more text.
///
/// **bold text**
///
/// *emph text*
///
/// `code span`
///
/// `multiline
/// code span`
///
/// ```
/// A code snippet
///
/// And more code
/// ```
///
/// ```foo, bar, baz
/// A code snippet 2
/// "```"
/// ```
///
///     code block
///     block code
///
/// > block quote
/// > more quote
///
/// <div>
/// Some html
/// <div>some more html</div>
/// </div>
///
/// 1. Ordered list item
/// 2. Ordered list item 2
///
/// * Unordered list item
/// * Unordered list item 2
///
/// | Table header 1   | Table header 2
/// |------------------|---------------
/// | Foo              | Bar
///
/// [inline link](http://example.com)
/// [inline link](</uri with spaces>)
/// [inline link](http://example.com "link title")
/// [inline link](http://example.com 'link title')
/// [inline link](http://example.com (link title))
///
/// [short link]
///
/// [full link][dst]
///
/// [`short link in backticks`]
///
/// [multiline
/// short link]
///
/// ![image](/url "title")
///
/// <http://example.com>
///
/// [link def 1]: http://example.com
/// [link def 2]: index.html
/// [`link def in backticks`]: http://example.com
/// [not an url 1]: foobar
/// [not an url 2]: foo-bar
/// [multiline]:
/// foobar
/// [hash only]: #hash
/// [rust path]: foo::bar::baz
/// [rust path with disambiguator 1]: fn@foo::bar::baz
/// [rust path with disambiguator 2]: foo::bar::baz!()
/// [rust path with disambiguator 3]: foo::bar::baz()
/// [rust path with disambiguator 4]: foo::bar::baz!
/// [rust path with hash]: foo::bar::baz#quux
/// [rust path with disambiguator and hash 1]: foo::bar::baz!()#hash
/// [rust path with disambiguator and hash 2]: fn@foo::bar::baz#hash
/// [rust path generics]: Vec<i32>
/// [rust path generics]: Vec<i32>::new()
/// [rust path in backticks 1]: foo`
/// [rust path in backticks 2]: `foo
/// [rust path in backticks 3]: `foo`
/// [rust path in backticks 4]: ```foo``
/// [rust path in backticks 5]: `fn@Vec::new#hash`
fn foo1() {}

/// [bar1] - rust path
/// [`bar2`] - rust path in backticks
/// [  bar3 ] - rust path with spaces
/// [ `bar4` ] - rust path in backticks with spaces
/// [label] - markdown label reference
///
/// [label]: func
fn foo2() {}

mod foo {
    //!
    //!
    //!

    ///
    ///
    ///
    fn bar() {}

    /**
     * starred line
       not starred line
     */
    fn baz() {}

    /** */
    fn quux() {}
}
