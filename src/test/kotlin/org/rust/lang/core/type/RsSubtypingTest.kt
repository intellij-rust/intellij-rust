package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.findSubtypes
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.RustIntegerType

class RsSubtypingTest : RsTestBase() {
    override val dataPath = ""

    fun assertConvertibleTo(@Language("Rust") code: String) {
        InlineFile(code)
        val (expr, data) = findElementAndDataInEditor<RsExpr>()
        val types = data.split(",").map(String::trim)
        assertThat(expr.type.findSubtypes(expr.project).map { it.toString() }.toList())
            .containsOnly(*types.toTypedArray())
    }

    fun `test reference subtyping`() {
        val base = RustIntegerType(RustIntegerType.Kind.i32)
        assertConvertibleTo("""
            fn main() {
                let x: &mut i32;
                x
              //^ &mut i32, & i32, *const i32, *mut i32
            }
""")
    }

    fun `test deref subtyping`() {
        assertConvertibleTo("""
            struct A {
                a : i32
            }

            pub trait Deref {
                type Target: ?Sized;
                fn deref(&self) -> &Self::Target;
            }

            impl Deref<Target=i32> for A {
                fn deref(&self) -> i32 {
                    return self.a;
                }
            }

            fn main() {
                let x: & A;
                x
              //^ & A, *const A, i32
            }
"""     )
    }
}
