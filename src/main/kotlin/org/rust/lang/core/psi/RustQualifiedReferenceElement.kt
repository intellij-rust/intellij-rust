package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustQualifiedPath

interface RustQualifiedReferenceElement : RustReferenceElement
                                        , RustQualifiedPath {

    companion object {
        val SELF_TYPE_REF = "Self"
    }

    override val qualifier: RustQualifiedReferenceElement?

}

sealed class RelativeModulePrefix {
    object Invalid: RelativeModulePrefix()
    object NotRelative: RelativeModulePrefix()
    class AncestorModule(val level: Int): RelativeModulePrefix() {
        init { require(level >= 0) }
    }
}
