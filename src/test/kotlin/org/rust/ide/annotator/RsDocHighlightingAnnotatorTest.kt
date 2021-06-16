/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.intellij.lang.annotations.Language
import org.rust.ide.colors.RsColor

class RsDocHighlightingAnnotatorTest : RsAnnotatorTestBase(RsDocHighlightingAnnotator::class) {
    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    fun `test line doc`() = checkHighlightingStrict("""
        //! <DOC_HEADING># Iterator</DOC_HEADING>
        //!
        //! The heart and soul of this module is the <DOC_LINK>[`Iterator`]</DOC_LINK> trait. The core of
        //! <DOC_LINK>[`Iterator`]</DOC_LINK> looks like this:
        //!
        //! <DOC_CODE>```</DOC_CODE>
        //! <DOC_CODE>trait Iterator {</DOC_CODE>
        //! <DOC_CODE>    type Item;</DOC_CODE>
        //! <DOC_CODE>    fn next(&mut self) -> Option<Self::Item>;</DOC_CODE>
        //! <DOC_CODE>}</DOC_CODE>
        //! <DOC_CODE>```</DOC_CODE>
        //!
        //! An iterator has a method, <DOC_LINK>[`next()`]</DOC_LINK>, which when called, returns an
        //! <DOC_LINK>[`Option`]</DOC_LINK><DOC_CODE>`<Item>`</DOC_CODE>. <DOC_LINK>[`next()`]</DOC_LINK> will return <DOC_CODE>`Some(Item)`</DOC_CODE> as long as there
        //! are elements, and once they've all been exhausted, will return <DOC_CODE>`None`</DOC_CODE> to
        //! indicate that iteration is finished. Individual iterators may choose to
        //! resume iteration, and so calling <DOC_LINK>[`next()`]</DOC_LINK> again may or may not eventually
        //! start returning <DOC_CODE>`Some(Item)`</DOC_CODE> again at some point.
        //!
        //! <DOC_LINK>[`Iterator`]</DOC_LINK>'s full definition includes a number of other methods as well,
        //! but they are default methods, built on top of <DOC_LINK>[`next()`]</DOC_LINK>, and so you get
        //! them for free.
        //!
        //! Iterators are also composable, and it's common to chain them together to do
        //! more complex forms of processing. See the <DOC_LINK>[Adapters](#adapters)</DOC_LINK> section
        //! below for more details.
        //!
        //! <DOC_LINK>[`Iterator`]: trait.Iterator.html</DOC_LINK>
        //! <DOC_LINK>[`next()`]: trait.Iterator.html#tymethod.next</DOC_LINK>
        //! <DOC_LINK>[`Option`]: ../../std/option/enum.Option.html</DOC_LINK>

        /// The <DOC_CODE>`Option`</DOC_CODE> type. See <DOC_LINK>[the module level documentation](index.html)</DOC_LINK> for more.

            /// Lorem ipsum dolor sit amet, consectetur adipiscing elit,
            /// sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
            /// Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
            /// nisi ut aliquip ex ea commodo consequat.
    """)

    fun `test block doc`() = checkHighlightingStrict("""
        /*!
         *  <DOC_HEADING># Iterator</DOC_HEADING>
         *
         *  The heart and soul of this module is the <DOC_LINK>[`Iterator`]</DOC_LINK> trait. The core of
         *  <DOC_LINK>[`Iterator`]</DOC_LINK> looks like this:
         *
         *  <DOC_CODE>```</DOC_CODE>
         *  <DOC_CODE>trait Iterator {</DOC_CODE>
         *  <DOC_CODE>    type Item;</DOC_CODE>
         *  <DOC_CODE>    fn next(&mut self) -> Option<Self::Item>;</DOC_CODE>
         *  <DOC_CODE>}</DOC_CODE>
         *  <DOC_CODE>```</DOC_CODE>
         *
         *  An iterator has a method, <DOC_LINK><DOC_LINK>[`next()`]</DOC_LINK></DOC_LINK>, which when called, returns an
         *  <DOC_LINK>[`Option`]</DOC_LINK><DOC_CODE>`<Item>`</DOC_CODE>. <DOC_LINK>[`next()`]</DOC_LINK> will return <DOC_CODE>`Some(Item)`</DOC_CODE> as long as there
         *  are elements, and once they've all been exhausted, will return <DOC_CODE>`None`</DOC_CODE> to
         *  indicate that iteration is finished. Individual iterators may choose to
         *  resume iteration, and so calling <DOC_LINK>[`next()`]</DOC_LINK> again may or may not eventually
         *  start returning <DOC_CODE>`Some(Item)`</DOC_CODE> again at some point.
         *
         *  <DOC_LINK>[`Iterator`]</DOC_LINK>'s full definition includes a number of other methods as well,
         *  but they are default methods, built on top of <DOC_LINK>[`next()`]</DOC_LINK>, and so you get
         *  them for free.
         *
         *  Iterators are also composable, and it's common to chain them together to do
         *  more complex forms of processing. See the <DOC_LINK>[Adapters](#adapters)</DOC_LINK> section
         *  below for more details.
         *
         *  <DOC_LINK>[`Iterator`]: trait.Iterator.html</DOC_LINK>
         *  <DOC_LINK>[`next()`]: trait.Iterator.html#tymethod.next</DOC_LINK>
         *  <DOC_LINK>[`Option`]: ../../std/option/enum.Option.html</DOC_LINK>
         */

        /**The <DOC_CODE>`Option`</DOC_CODE> type. See <DOC_LINK>[the module level documentation](index.html)</DOC_LINK> for more.*/

            /** Lorem ipsum dolor sit amet, consectetur adipiscing elit,
             *   sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
             *   Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
             *   nisi ut aliquip ex ea commodo consequat.
             */

        /**
            missing asterisk
         */

        /**
         <DOC_CODE>   ```</DOC_CODE>
         <DOC_CODE>   missing asterisk</DOC_CODE>
         <DOC_CODE>   ```</DOC_CODE>
         */
    """)

    fun `test doc heading`() = checkHighlightingStrict("""
        /// This is Setext level 1 header!
        /// ==============================
        ///
        /// This is Setext level 2 header!
        /// ------------------------------
        ///
        /// <DOC_HEADING># foo</DOC_HEADING>
        /// <DOC_HEADING>## foo</DOC_HEADING>
        /// <DOC_HEADING>### foo</DOC_HEADING>
        /// <DOC_HEADING>#### foo</DOC_HEADING>
        /// <DOC_HEADING>##### foo</DOC_HEADING>
        /// <DOC_HEADING>###### foo</DOC_HEADING>
        /// ####### foo
        /// #5 bolt
        /// #hashtag
        /// #	there is tab here
        /// \## foo
        /// <DOC_HEADING> ### foo</DOC_HEADING>
        /// <DOC_HEADING>  ## foo</DOC_HEADING>
        /// <DOC_HEADING>   # foo</DOC_HEADING>
        /// <DOC_CODE>    # not a header</DOC_CODE>
        /// totally # not a header
        /// these two guys have to be at the end of the test comment
        /// <DOC_HEADING>##</DOC_HEADING>
        /// <DOC_HEADING>#</DOC_HEADING>

        /**<DOC_HEADING> # foo</DOC_HEADING> */
    """)

    fun `test doc link`() = checkHighlightingStrict("""
        /// <DOC_LINK>[link](/uri "title")</DOC_LINK>
        /// <DOC_LINK>[link](/uri)</DOC_LINK>
        /// <DOC_LINK>[link]()</DOC_LINK>
        /// <DOC_LINK>[link](<>)</DOC_LINK>
        /// <DOC_LINK>[link](/my uri)</DOC_LINK>
        /// <DOC_LINK>[link](</my uri>)</DOC_LINK>
        /// <DOC_LINK>[link](\(foo\))</DOC_LINK>
        /// <DOC_LINK>[link](<foo(and(bar))>)</DOC_LINK>
        /// <DOC_LINK>[link](/url "title")</DOC_LINK>
        /// <DOC_LINK>[link](/url 'title')</DOC_LINK>
        /// <DOC_LINK>[link](/url (title))</DOC_LINK>
        /// <DOC_LINK>[link](/url "title \"&quot;")</DOC_LINK>
        /// <DOC_LINK>[link](/url 'title "and" title')</DOC_LINK>
        /// <DOC_LINK>[link \[bar](/uri)</DOC_LINK>
        /// <DOC_LINK>[link *foo **bar** `#`*](/uri)</DOC_LINK>
        /// *<DOC_LINK>[foo*](/uri)</DOC_LINK>
        /// <DOC_LINK>[foo *bar](baz*)</DOC_LINK>
        /// <DOC_LINK>[foo][bar]</DOC_LINK>
        /// <DOC_LINK>[link \[bar][ref]</DOC_LINK>
        /// <DOC_LINK>[link *foo **bar** `#`*][ref]</DOC_LINK>
        /// *<DOC_LINK>[foo*][ref]</DOC_LINK>
        /// <DOC_LINK>[foo][]</DOC_LINK>
        /// <DOC_LINK>[foo]</DOC_LINK>
        /// [vec[]]
        /// [vec[][][]]
        /// [box<<DOC_LINK>[T]</DOC_LINK>>]
        ///
        /// [multiline
        /// short link]
        ///
        /// <DOC_LINK>[bar]: /url "title"</DOC_LINK>
        /// <DOC_LINK>[ref]: /uri</DOC_LINK>
        /// [multiline]:
        /// foobar
    """)

    fun `test code span`() = checkHighlightingStrict("""
        /// <DOC_CODE>`foo`</DOC_CODE>
        /// <DOC_CODE>`foo\`</DOC_CODE>bar`
        ///
        /// <DOC_CODE>`multiline</DOC_CODE>
        /// <DOC_CODE>code span`</DOC_CODE>
        ///
        /// <DOC_CODE>`foo`</DOC_CODE> <DOC_CODE>`bar`</DOC_CODE>
    """)

    fun `test code fence`() = checkHighlightingStrict("""
        /// <DOC_CODE>```</DOC_CODE><DOC_CODE>foo</DOC_CODE>
        /// <DOC_CODE>bar</DOC_CODE>
        /// <DOC_CODE>```</DOC_CODE>

        /// <DOC_CODE>~~~</DOC_CODE><DOC_CODE>md</DOC_CODE>
        /// <DOC_CODE># Not a header</DOC_CODE>
        /// <DOC_CODE>```</DOC_CODE>
        /// <DOC_CODE>nesting</DOC_CODE>
        /// <DOC_CODE>```</DOC_CODE>
        /// <DOC_CODE>~~~</DOC_CODE>

        /// <DOC_CODE>```</DOC_CODE>
        /// <DOC_CODE>foo</DOC_CODE>
        /// <DOC_CODE>~~~</DOC_CODE>
        /// <DOC_CODE>bar</DOC_CODE>
    """)

    fun `test header after code fence`() = checkHighlightingStrict("""
        /// <DOC_CODE>```</DOC_CODE>
        /// <DOC_CODE>code</DOC_CODE>
        /// <DOC_CODE>```</DOC_CODE>
        /// <DOC_HEADING># header</DOC_HEADING>
    """)

    fun `test heading with formatting`() = checkHighlightingStrict("""
        /// <DOC_HEADING># header <DOC_CODE>`code`</DOC_CODE> *emphasis*</DOC_HEADING>
    """)

    private fun checkHighlightingStrict(@Language("Rust") text: String) =
        annotationFixture.checkHighlighting(text, ignoreExtraHighlighting = false)
}
