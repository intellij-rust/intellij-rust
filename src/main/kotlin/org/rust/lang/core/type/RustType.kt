package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

interface RustType {

    fun <T> accept(visitor: RustTypeVisitor<T>): T

}
