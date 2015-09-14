// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustEnumArgs extends RustCompositeElement {

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustStructDeclField> getStructDeclFieldList();

  @Nullable
  RustTypeSums getTypeSums();

  @Nullable
  PsiElement getEq();

  @Nullable
  PsiElement getLbrace();

  @Nullable
  PsiElement getLparen();

  @Nullable
  PsiElement getRbrace();

  @Nullable
  PsiElement getRparen();

}
