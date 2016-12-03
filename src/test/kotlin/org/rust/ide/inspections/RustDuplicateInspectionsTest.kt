package org.rust.ide.inspections

import org.rust.ide.inspections.duplicates.RustDuplicateStructFieldInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitConstantInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitMethodInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitTypeInspection

/**
 * Tests for Duplicate * inspections
 */
class RustDuplicateInspectionsTest : RustInspectionsTestBase() {

    fun testDuplicateField() = checkByText<RustDuplicateStructFieldInspection>("""
        struct S {
            foo: i32,
            <error descr="Duplicate field 'foo: String'">foo: String</error>,
        }
        enum E {
            V { foo: i32, bar: String, <error>foo: i32</error> }
        }
    """)

    fun testDuplicateTraitConstant() = checkByText<RustDuplicateTraitConstantInspection>("""
        trait T {
             const B: i32;
             const <error descr="Duplicate trait constant 'B'">B</error>: i32;
             const <error descr="Duplicate trait constant 'B'">B</error>: i32 = 1;
        }
    """)

    fun testDuplicateTraitMethod() = checkByText<RustDuplicateTraitMethodInspection>("""
        trait T {
            fn foo(&self) -> f64;
            fn <error descr="Duplicate trait method 'foo'">foo</error>(&self) -> f64;
        }
    """)

    fun testDuplicateTraitType() = checkByText<RustDuplicateTraitTypeInspection>("""
    trait T {
        type A;
        type <error descr="Duplicate associated type 'A'">A</error>;
        type <error descr="Duplicate associated type 'A'">A</error>: bool;
    }
    """)
}
