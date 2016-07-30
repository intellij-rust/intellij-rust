package org.rust.lang.utils

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustKeywordTokenType

val RustKeywords = IElementType.enumerate { it is RustKeywordTokenType }.map { it.toString() }

val RustPrimitiveTypes = arrayOf(
    "bool",
    "char",
    "i8",
    "i16",
    "i32",
    "i64",
    "u8",
    "u16",
    "u32",
    "u64",
    "isize",
    "usize",
    "f32",
    "f64",
    "str"
)

fun String.isRustIdentifier(withPrimitives: Boolean = true): Boolean =
    !isRustReserved(withPrimitives) && isRustIdentifierString()

fun String.isRustReserved(withPrimitives: Boolean = true): Boolean =
    this in RustKeywords || (withPrimitives && this in RustPrimitiveTypes)

fun String.isRustIdentifierString(): Boolean =
    !isEmpty()
        && Character.isJavaIdentifierStart(this[0])
        && drop(1).all { Character.isJavaIdentifierPart(it) }
