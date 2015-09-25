// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustLambdaExpr extends RustExpr {

  @Nullable
  RustCommaSeparatedList getCommaSeparatedList();

  @Nullable
  RustExpr getExpr();

  @Nullable
  RustRetType getRetType();

  @Nullable
  PsiElement getMove();

  @Nullable
  PsiElement getOror();

}
