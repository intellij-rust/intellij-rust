/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCfgAttributeCompletionProviderTest : RsCompletionTestBase() {
    fun `test complete unix`() = doSingleCompletion("""
        #[cfg(un/*caret*/)]
        fn foo() {}
    """, """
        #[cfg(unix/*caret*/)]
        fn foo() {}
    """)

    fun `test complete windows`() = doSingleCompletion("""
        #[cfg(wind/*caret*/)]
        fn foo() {}
    """, """
        #[cfg(windows/*caret*/)]
        fn foo() {}
    """)

    fun `test complete all`() = doSingleCompletion("""
        #[cfg(al/*caret*/)]
        fn foo() {}
    """, """
        #[cfg(all(/*caret*/))]
        fn foo() {}
    """)

    fun `test complete target_os`() = doSingleCompletion("""
        #[cfg(taros/*caret*/)]
        fn foo() {}
    """, """
        #[cfg(target_os = "/*caret*/")]
        fn foo() {}
    """)

    fun `test do not insert paren if present`() = doSingleCompletion("""
        #[cfg(no/*caret*/(unix))]
        fn foo() {}
    """, """
        #[cfg(not(/*caret*/unix))]
        fn foo() {}
    """)

    fun `test do not insert value if present`() = doSingleCompletion("""
        #[cfg(taos/*caret*/ = "linux")]
        fn foo() {}
    """, """
        #[cfg(target_os = "/*caret*/linux")]
        fn foo() {}
    """)

    fun `test nested predicates`() = doSingleCompletion("""
        #[cfg(not(and(unix, target_endi/*caret*/)))]
        fn foo() {}
    """, """
        #[cfg(not(and(unix, target_endian = "/*caret*/")))]
        fn foo() {}
    """)

    fun `test no completion in feature names 1`() = checkNoCompletion("""
        #[cfg(feature = "/*caret*/")]
        fn foo() {}
    """)

    fun `test no completion in feature names 2`() = checkNoCompletion("""
        #[cfg(feature = /*caret*/)]
        fn foo() {}
    """)

    fun `test complete in cfg_attr 1`() = doSingleCompletion("""
        #[cfg_attr(un/*caret*/)]
        fn foo() {}
    """, """
        #[cfg_attr(unix/*caret*/)]
        fn foo() {}
    """)

    fun `test complete in cfg_attr 2`() = doSingleCompletion("""
        #[cfg_attr(un/*caret*/, foo)]
        fn foo() {}
    """, """
        #[cfg_attr(unix/*caret*/, foo)]
        fn foo() {}
    """)

    fun `test complete in cfg_attr 3`() = doSingleCompletion("""
        #[cfg_attr(foobar, cfg_attr(un/*caret*/, foo))]
        fn foo() {}
    """, """
        #[cfg_attr(foobar, cfg_attr(unix/*caret*/, foo))]
        fn foo() {}
    """)

    fun `test no completion in second argument of cfg_attr`() = checkNoCompletion("""
        #[cfg_attr(unix, window/*caret*/)]
        fn foo() {}
    """)

    fun `test complete in doc cfg`() = doSingleCompletion("""
        #[doc(cfg(un/*caret*/))]
        fn foo() {}
    """, """
        #[doc(cfg(unix/*caret*/))]
        fn foo() {}
    """)

    fun `test feature`() = checkContainsCompletion("feature", """
        #[cfg(feat/*caret*/)]
        fn foo() {}
    """)
}
