/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import org.rust.FileTreeBuilder
import org.rust.fileTree
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.resolve.RsResolveTestBase

class RsCfgFeatureReferenceProviderTest : RsResolveTestBase() {
    fun `test cfg simple`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg(feature = "foo")]
            fn foo() {}    //^ Cargo.toml
        """)
    }

    fun `test cfg_attr simple`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg_attr(feature = "foo", deny(all))]
            fn foo() {}         //^ Cargo.toml
        """)
    }

    fun `test doc cfg simple`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[doc(cfg(feature = "foo"))]
            fn foo() {}        //^ Cargo.toml
        """)
    }

    fun `test not resolved`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            bar = []
        """)
        rust("main.rs", """
            #[cfg(feature = "foo")]
            fn foo() {}    //^ unresolved
        """)
    }

    fun `test nested`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg(not(feature = "foo"))]
            fn foo() {}        //^ Cargo.toml
        """)
    }

    fun `test inner attr`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            fn foo() {
                #![cfg(feature = "foo")]
            }                   //^ Cargo.toml
        """)
    }

    fun `test optional dependency`() = doResolveTest {
        toml("Cargo.toml", """
            [dependencies]
            foo = { version = "*", optional = true }
        """)
        rust("main.rs", """
            #[cfg(feature = "foo")]
            fn foo() {}    //^ Cargo.toml
        """)
    }

    fun `test cfg in cfg_attr`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg_attr(windows, cfg(feature = "foo"))]
            fn foo() {}                      //^ Cargo.toml
        """)
    }

    fun `test cfg_attr in cfg_attr`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg_attr(windows, cfg_attr(feature = "foo", deny(all)))]
            fn foo() {}                           //^ Cargo.toml
        """)
    }

    fun `test cfg in cfg_attr nested`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg_attr(windows, cfg_attr(foobar, cfg(feature = "foo")))]
            fn foo() {}                                       //^ Cargo.toml
        """)
    }

    fun `test doc cfg in cfg_attr`() = doResolveTest {
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg_attr(windows, doc(cfg(feature = "foo")))]
            fn foo() {}                          //^ Cargo.toml
        """)
    }

    private fun doResolveTest(builder: FileTreeBuilder.() -> Unit) {
        val fileTree = fileTree(builder)
        resolveByFileTree<RsLitExpr>(fileTree, resolveFileProducer = this::getActualResolveFile)
    }
}
