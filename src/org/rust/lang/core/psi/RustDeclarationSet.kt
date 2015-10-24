package org.rust.lang.core.psi

interface RustDeclarationSet {
    fun listDeclarations(): List<RustPatIdent>

}