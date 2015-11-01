package org.rust.lang.core.psi

public interface RustItem : RustNamedElement {

    val attrs : List<RustOuterAttr>?
        get

    val vis: RustVis?
        get

}

