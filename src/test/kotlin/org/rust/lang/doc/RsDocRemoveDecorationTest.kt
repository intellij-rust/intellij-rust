/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc

import com.intellij.openapi.util.text.StringUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.lang.doc.psi.RsDocKind
import org.rust.lang.doc.psi.RsDocKind.*

@RunWith(Parameterized::class)
class RsDocRemoveDecorationTest(
    private val kind: RsDocKind,
    private val comment: String,
    private val content: String
) {
    @Test
    fun test() {
        val commentNorm = StringUtil.convertLineSeparators(comment)
        val contentNorm = StringUtil.convertLineSeparators(content)

        assertEquals(contentNorm,
            kind.removeDecoration(commentNorm.splitToSequence('\n')).joinToString("\n").trim())
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: {0} \"{1}\" → \"{2}\"")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf(InnerEol, "//! foo", "foo"),
            arrayOf(OuterEol, "/// foo", "foo"),

            arrayOf(OuterEol,
                //language=Rust
                """/// foo
                   /// bar""",
                "foo\nbar"),

            arrayOf(OuterEol,
                //language=Rust
                """///   foo
                   ///   bar""",
                "foo\nbar"),

            arrayOf(OuterEol,
                //language=Rust
                """/// foo
                   /// ```
                   /// code {
                   ///     bar
                   /// }
                   /// ```""",
                """foo
```
code {
    bar
}
```"""),

            arrayOf(OuterEol,
                //language=Rust
                """///   foo
                   ///   ```
                   ///   code {
                   ///       bar
                   ///   }
                   ///   ```""",
                """foo
```
code {
    bar
}
```"""),

            arrayOf(OuterBlock,
                //language=Rust
                """/** foo
                    *  bar
                    */""",
                "foo\nbar"),

            arrayOf(InnerBlock,
                //language=Rust
                """/*! foo
                    *  bar
                    */""",
                "foo\nbar"),

            arrayOf(OuterBlock,
                //language=Rust
                """/** foo
                    *  ```
                    *  code {
                    *      bar
                    *  }
                    *  ```
                    */""",
                """foo
```
code {
    bar
}
```"""),

            arrayOf(OuterBlock,
                //language=Rust
                """/**   foo
                    *    ```
                    *    code {
                    *        bar
                    *    }
                    *    ```
                    */""",
                """foo
```
code {
    bar
}
```"""),

            arrayOf(OuterBlock,
                //language=Rust
                "/** foo */",
                "foo"),

            arrayOf(OuterBlock,
                //language=Rust
                """/** foo
                    *  bar */""",
                "foo\nbar"),

            arrayOf(OuterBlock,
                //language=Rust
                """/** foo
                   |bar * bar
                   |*/""".trimMargin(),
                "foo\nbar * bar"),

            arrayOf(Attr, "foo\nbar", "foo\nbar"),
            arrayOf(Attr, " *foo1", "*foo1"),
            arrayOf(Attr, "\n * foo2\n", "foo2"),
            arrayOf(Attr, "\n * foo3\n * foo4\n ", "foo3\nfoo4"),
            arrayOf(Attr, "\n  * foo5\n  * foo6\n  ", "foo5\nfoo6"),
            arrayOf(Attr, "\n   * foo7\n * foo8\n ", "* foo7\n* foo8"),
            arrayOf(Attr, "\n  * foo9\n \n  *\n  *", "* foo9\n\n*\n*"),
            arrayOf(Attr, "\n  * foo10\na\n  *\n  *", "* foo10\na\n*\n*"),
            arrayOf(Attr, "\n \n foo11\n", "foo11"),
            arrayOf(Attr, "\n foo12\n ", "foo12"),
        )
    }
}
