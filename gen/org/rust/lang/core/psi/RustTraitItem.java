// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTraitItem extends RustCompositeElement {

  @NotNull
  RustGenericParams getGenericParams();

  @NotNull
  List<RustTraitConst> getTraitConstList();

  @NotNull
  List<RustTraitMethod> getTraitMethodList();

  @NotNull
  List<RustTraitType> getTraitTypeList();

  @Nullable
  RustTypeParamBounds getTypeParamBounds();

  @Nullable
  RustWhereClause getWhereClause();

  @Nullable
  PsiElement getFor();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getQ();

  @NotNull
  PsiElement getRbrace();

  @NotNull
  PsiElement getTrait();

  @Nullable
  PsiElement getUnsafe();

}
