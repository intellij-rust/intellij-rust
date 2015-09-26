// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTypeMethod extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @Nullable
  RustAnonParams getAnonParams();

  @NotNull
  RustGenericParams getGenericParams();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @Nullable
  RustRetType getRetType();

  @Nullable
  RustTypeAscription getTypeAscription();

  @Nullable
  RustWhereClause getWhereClause();

  @Nullable
  PsiElement getAnd();

  @Nullable
  PsiElement getExtern();

  @NotNull
  PsiElement getFn();

  @NotNull
  PsiElement getIdentifier();

  @Nullable
  PsiElement getLifetime();

  @NotNull
  PsiElement getLparen();

  @Nullable
  PsiElement getMut();

  @Nullable
  PsiElement getPub();

  @NotNull
  PsiElement getRparen();

  @Nullable
  PsiElement getSelf();

  @NotNull
  PsiElement getSemicolon();

  @Nullable
  PsiElement getStaticLifetime();

  @Nullable
  PsiElement getUnsafe();

}
