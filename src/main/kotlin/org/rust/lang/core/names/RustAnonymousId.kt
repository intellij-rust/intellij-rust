package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustAnonymousIdPart

/**
 * Name designating head-less 'identifier' corresponding to Rust's
 * anonymous crate-scope
 */
object RustAnonymousId : RustQualifiedName(RustAnonymousIdPart)
