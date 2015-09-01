// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.rust.lang.core.psi.RustCompositeElement;

public interface RustUseDecl extends RustCompositeElement {

  @Nullable
  RustPath getPath();

  @Nullable
  RustPathGlob getPathGlob();

  @Nullable
  PsiElement getIdentifier();

}
