package org.rust.lang.core.psi

val RustPathElement.isPrimitive: Boolean get() = path == null && referenceName in primitives

private val primitives = setOf(
    "i8", "i16", "i32", "i64", "isize",
    "u8", "u16", "u32", "u64", "usize",
    "char", "str",
    "f32", "f64",
    "bool"
)
