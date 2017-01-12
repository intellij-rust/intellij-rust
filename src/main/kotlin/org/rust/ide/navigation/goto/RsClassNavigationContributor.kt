package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.stubs.index.RustGotoClassIndex

class RsClassNavigationContributor
    : RsNavigationContributorBase<RsNamedElement>(RustGotoClassIndex.KEY, RsNamedElement::class.java)

