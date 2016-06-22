package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RustStructOrEnumItemElement
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustClassNavigationContributor
    : RustNavigationContributorBase<RustStructOrEnumItemElement>(RustStructOrEnumIndex.KEY, RustStructOrEnumItemElement::class.java)

