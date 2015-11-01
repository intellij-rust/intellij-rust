package org.rust.lang.core.psi

public interface RustItem : RustNamedElement {

    fun getAttrs() : List<RustOuterAttr>?

    fun getVis() : RustVis?

}

