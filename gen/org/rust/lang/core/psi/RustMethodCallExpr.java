// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustMethodCallExpr extends RustExpr {

  @Nullable
  RustArgList getArgList();

  @NotNull
  RustExpr getExpr();

  @Nullable
  RustGenericArgs getGenericArgs();

  @Nullable
  PsiElement getColoncolon();

  @NotNull
  PsiElement getDot();

  @Nullable
  PsiElement getIdentifier();

}
