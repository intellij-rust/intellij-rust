// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustEnumItem extends RustCompositeElement {

  @NotNull
  List<RustEnumDef> getEnumDefList();

  @NotNull
  RustGenericParams getGenericParams();

  @Nullable
  RustWhereClause getWhereClause();

  @NotNull
  PsiElement getEnum();

  @NotNull
  PsiElement getIdentifier();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
