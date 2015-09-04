// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustUseDecl extends RustCompositeElement {

  @Nullable
  RustPath getPath();

  @Nullable
  RustPathGlob getPathGlob();

  @Nullable
  PsiElement getAs();

  @Nullable
  PsiElement getPriv();

  @Nullable
  PsiElement getPub();

  @NotNull
  PsiElement getUse();

  @Nullable
  PsiElement getIdentifier();

}
