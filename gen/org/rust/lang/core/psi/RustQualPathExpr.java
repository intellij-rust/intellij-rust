// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustQualPathExpr extends RustExpr {

  @NotNull
  RustPathExprPart getPathExprPart();

  @Nullable
  RustTraitRef getTraitRef();

  @NotNull
  RustTypeSum getTypeSum();

  @Nullable
  PsiElement getAs();

  @NotNull
  PsiElement getColoncolon();

  @NotNull
  PsiElement getGt();

  @NotNull
  PsiElement getLt();

}
