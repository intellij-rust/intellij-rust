// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTraitConst extends RustCompositeElement {

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @Nullable
  RustTypeAscription getTypeAscription();

  @NotNull
  PsiElement getConst();

  @Nullable
  PsiElement getEq();

  @NotNull
  PsiElement getIdentifier();

  @NotNull
  PsiElement getSemicolon();

}
