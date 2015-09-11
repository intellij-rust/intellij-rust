// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustQualRefExpr extends RustExpr {

  @NotNull
  RustQualRefExprPart getQualRefExprPart();

  @NotNull
  PsiElement getDot();

  @NotNull
  PsiElement getIdentifier();

}
