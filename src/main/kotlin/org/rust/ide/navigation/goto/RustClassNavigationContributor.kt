package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustClassNavigationContributor
    : RustNavigationContributorBase<RustStructOrEnum>(RustStructOrEnumIndex.KEY, RustStructOrEnum::class.java)

