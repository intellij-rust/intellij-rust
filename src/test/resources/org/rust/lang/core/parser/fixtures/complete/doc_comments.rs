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
/// #	there is a tab here (not a header)
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
/// [link def]: http://example.com
/// [`link def in backticks`]: http://example.com
/// [not an url]: foobar
/// [multiline]:
/// foobar
/// [hash only]: #hash
/// [rust path]: foo::bar::baz
/// [rust path with disambiguator 1]: fn@foo::bar::baz
/// [rust path with disambiguator 2]: foo::bar::baz!()
/// [rust path with disambiguator 3]: foo::bar::baz()
/// [rust path with disambiguator 4]: foo::bar::baz!
/// [rust path with hash]: foo::bar::baz#quux
/// [rust path with tail disambiguator and hash]: foo::bar::baz!()#quux
fn foo() {}

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
