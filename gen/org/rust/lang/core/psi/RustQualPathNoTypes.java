// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustQualPathNoTypes extends RustCompositeElement {

  @Nullable
  RustPathWithoutColons getPathWithoutColons();

  @NotNull
  RustTypeSum getTypeSum();

  @Nullable
  PsiElement getAs();

  @NotNull
  PsiElement getGt();

  @NotNull
  PsiElement getLt();

}
