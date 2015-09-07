// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustStaticItem extends RustCompositeElement {

  @NotNull
  RustDeclItem getDeclItem();

  @NotNull
  RustExpr getExpr();

  @NotNull
  PsiElement getEq();

  @Nullable
  PsiElement getMut();

  @NotNull
  PsiElement getSemicolon();

  @NotNull
  PsiElement getStatic();

}
