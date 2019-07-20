/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("DEPRECATION")

package org.rust.ide.clones

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.clones.DuplicateInspection
import com.jetbrains.clones.configuration.DuplicateInspectionConfiguration
import com.jetbrains.clones.index.HashFragmentIndex
import com.jetbrains.clones.languagescope.common.CommonDuplicateIndexConfiguration
import org.intellij.lang.annotations.Language
import org.rust.ide.inspections.RsInspectionsTestBase

// Based on [com.jetbrains.clones.DuplicateBaseTest]
class RsDuplicateInspectionTest : RsInspectionsTestBase(DuplicateInspection::class) {

    private val scope = RsDuplicateScope()
    private val inspectionState by lazy { (inspection as DuplicateInspection).state.findConfiguration(scope)!! }
    private val indexState by lazy { scope.indexConfiguration }

    fun `test duplicate`() = doTest("""
        fn main() {
            /*weak_warning*/for i in 1..10 {
                println!("{}", i);
            }/*weak_warning**/
            /*weak_warning*/for i in 1..10 {
                println!("{}", i);
            }/*weak_warning**/
        }
    """)

    fun `test ignore some nodes`() = doTest("""
        fn foo(e: Enum) {
            /*weak_warning*/for i in 1..10 {
                match e {
                    Enum::A(y) => { println!("{}", i * y); },
                    Enum::B(x) => { println!("{}", i + x); },
                }
            }/*weak_warning**/
            /*weak_warning*/for i in 1 .. /*comment*/10 {
                // Another comment
                match e {
                    Enum::A(y) => { println!( "{}", i * y ); },
                    Enum::B(x) => { println!("{}", i + x); }
                }
            }/*weak_warning**/
        }
    """)

    fun `test exclude blocks from analysis`() = doTest("""
        fn main() {
            for i in 1..8 {
                /*weak_warning*/println!("{}", 2i32.pow(i));/*weak_warning**/
            }
            for i in 1..16 {
                /*weak_warning*/println!("{}", 2i32.pow(i));/*weak_warning**/
            }
        }
    """)

    fun `test exclude members from analysis`() = doTest("""
        impl Foo for i32 {
            type Bar = i32;
            fn baz() {}
        }

        impl Foo for String {
            type Bar = i32;
            fn baz() {}
        }
    """)

    fun `test exclude where clause from analysis`() = doTest("""
        fn foo<M, F>() where M: Debug, F: FnMut(&M) -> bool {}
        fn bar<M, F>() where M: Debug, F: FnMut(&M) -> bool {}
    """)

    fun `test exclude value argument and value parameter lists from analysis`() = doTest("""
        fn foo(a: i32, b: u64, c: char, s: &str) {}
        fn bar(a: i32, b: u64, c: char, s: &str) {}

        fn main() {
            foo(a + 1, b, baz(), "foo");
            bar(a + 1, b, baz(), "foo");
        }
    """)

    fun `test exclude type argument and type parameter lists from analysis`() = doTest("""
        fn foo<V1: Debug, V2: Copy, F: Hash>() {}
        fn bar<V1: Debug, V2: Copy, F: Hash>() {}

        fn main() {
            foo::<Vec<i32>, i8, BTreeMap<String, u64>>();
            bar::<Vec<i32>, i8, BTreeMap<String, u64>>();
        }
    """)

    fun `test binary expr order`() = doTest("""
        fn foo(a: i32, b: i32) {
            /*weak_warning*/while a >= b || b < 7 && a != 123 {
                let c = 4 * a + b;
                println!("{} {}", c, a == b);
            }/*weak_warning**/

            /*weak_warning*/while a >= b || b < 7 && a != 123 {
                let c = b + a * 4;
                println!("{} {}", c, a == b);
            }/*weak_warning**/
        }
    """)

    fun `test anonymize literals`() = doTest("""
        fn main() {
            /*weak_warning*/for i in 1..10 {
                foo(i, true, "bar");
            }/*weak_warning**/
            /*weak_warning*/for i in 1..100 {
                foo(i, false, "baz");
            }/*weak_warning**/
        }
    """, anonymizeLiterals = true)

    fun `test anonymize identifiers`() = doTest("""
        fn main() {
            /*weak_warning*/for i in 1..10 {
                println!("{}", i)
            }/*weak_warning**/
            /*weak_warning*/for j in 1..10 {
                println!("{}", j)
            }/*weak_warning**/
        }
    """, anonymizeIdentifiers = true)

    fun `test anonymize functions & fields`() = doTest("""
        fn main() {
            /*weak_warning*/for i in 1..10 {
                let x = i.abc();
                println!("{}", bar(x).y)
            }/*weak_warning**/
            /*weak_warning*/for i in 1..10 {
                let x = i.xyz();
                println!("{}", foo(x).z)
            }/*weak_warning**/
        }
    """, anonymizeFunctions = true)

    private fun doTest(
        @Language("Rust") code: String,
        anonymizeLiterals: Boolean = false,
        anonymizeIdentifiers: Boolean = false,
        anonymizeFunctions: Boolean = false
    ) {
        configureIndex {
            windowSize = 10
            this.anonymizeLiterals = anonymizeLiterals
            this.anonymizeIdentifiers = anonymizeIdentifiers
            this.anonymizeFunctions = anonymizeFunctions
        }
        configureInspection {
            isEnabled = true
            minSize = 10
        }
        checkByText(code, checkWeakWarn = true)
    }

    private fun configureIndex(configure: CommonDuplicateIndexConfiguration.() -> Unit){
        indexState.configure()
        HashFragmentIndex.requestRebuild()
        @Suppress("UnstableApiUsage")
        FileBasedIndex.getInstance().ensureUpToDate(HashFragmentIndex.NAME, project, GlobalSearchScope.projectScope(project))
    }

    private fun configureInspection(configure: DuplicateInspectionConfiguration.() -> Unit){
        inspectionState.configure()
    }
}
