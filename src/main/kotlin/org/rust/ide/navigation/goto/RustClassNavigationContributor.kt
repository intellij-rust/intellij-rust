package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustGotoClassIndex

class RustClassNavigationContributor
    : RustNavigationContributorBase<RustNamedElement>(RustGotoClassIndex.KEY, RustNamedElement::class.java)

