// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustImplMethod extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  RustBlock getBlock();

  @NotNull
  RustGenericParams getGenericParams();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @Nullable
  RustParam getParam();

  @NotNull
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

  @Nullable
  PsiElement getStaticLifetime();

  @Nullable
  PsiElement getUnsafe();

}
