// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import org.rust.lang.core.psi.*;

public class RustTraitItemImpl extends RustNamedElementImpl implements RustTraitItem {

  public RustTraitItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitTraitItem(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public RustGenericParams getGenericParams() {
    return findNotNullChildByClass(RustGenericParams.class);
  }

  @Override
  @Nullable
  public RustTraitConst getTraitConst() {
    return findChildByClass(RustTraitConst.class);
  }

  @Override
  @Nullable
  public RustTraitMethod getTraitMethod() {
    return findChildByClass(RustTraitMethod.class);
  }

  @Override
  @Nullable
  public RustTraitType getTraitType() {
    return findChildByClass(RustTraitType.class);
  }

  @Override
  @Nullable
  public RustTypeParamBounds getTypeParamBounds() {
    return findChildByClass(RustTypeParamBounds.class);
  }

  @Override
  @Nullable
  public RustWhereClause getWhereClause() {
    return findChildByClass(RustWhereClause.class);
  }

  @Override
  @Nullable
  public PsiElement getFor() {
    return findChildByType(FOR);
  }

  @Override
  @NotNull
  public PsiElement getLbrace() {
    return findNotNullChildByType(LBRACE);
  }

  @Override
  @Nullable
  public PsiElement getQ() {
    return findChildByType(Q);
  }

  @Override
  @NotNull
  public PsiElement getRbrace() {
    return findNotNullChildByType(RBRACE);
  }

  @Override
  @NotNull
  public PsiElement getTrait() {
    return findNotNullChildByType(TRAIT);
  }

  @Override
  @Nullable
  public PsiElement getUnsafe() {
    return findChildByType(UNSAFE);
  }

}
