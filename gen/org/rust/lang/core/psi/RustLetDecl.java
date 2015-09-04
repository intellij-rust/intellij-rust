// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustLetDecl extends RustCompositeElement {

  @Nullable
  RustExpr getExpr();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getEq();

  @NotNull
  PsiElement getLet();

  @NotNull
  PsiElement getSemicolon();

  @Nullable
  PsiElement getIdentifier();

}
