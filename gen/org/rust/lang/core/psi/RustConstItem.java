// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustConstItem extends RustCompositeElement {

  @NotNull
  RustDeclItem getDeclItem();

  @NotNull
  RustExpr getExpr();

  @NotNull
  PsiElement getConst();

  @NotNull
  PsiElement getEq();

  @NotNull
  PsiElement getSemicolon();

}
