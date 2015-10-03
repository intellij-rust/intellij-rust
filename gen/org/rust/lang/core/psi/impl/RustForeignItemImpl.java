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

public class RustForeignItemImpl extends RustNamedElementImpl implements RustForeignItem {

  public RustForeignItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitForeignItem(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustAbi getAbi() {
    return findChildByClass(RustAbi.class);
  }

  @Override
  @NotNull
  public List<RustAnonParam> getAnonParamList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustAnonParam.class);
  }

  @Override
  @NotNull
  public List<RustExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustExpr.class);
  }

  @Override
  @Nullable
  public RustForeignFnItem getForeignFnItem() {
    return findChildByClass(RustForeignFnItem.class);
  }

  @Override
  @Nullable
  public RustGenericParams getGenericParams() {
    return findChildByClass(RustGenericParams.class);
  }

  @Override
  @Nullable
  public RustLifetimes getLifetimes() {
    return findChildByClass(RustLifetimes.class);
  }

  @Override
  @NotNull
  public List<RustOuterAttr> getOuterAttrList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustOuterAttr.class);
  }

  @Override
  @Nullable
  public RustPathWithoutColons getPathWithoutColons() {
    return findChildByClass(RustPathWithoutColons.class);
  }

  @Override
  @Nullable
  public RustQualPathNoTypes getQualPathNoTypes() {
    return findChildByClass(RustQualPathNoTypes.class);
  }

  @Override
  @Nullable
  public RustRetType getRetType() {
    return findChildByClass(RustRetType.class);
  }

  @Override
  @Nullable
  public RustTraitRef getTraitRef() {
    return findChildByClass(RustTraitRef.class);
  }

  @Override
  @NotNull
  public List<RustTypeSums> getTypeSumsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeSums.class);
  }

  @Override
  @Nullable
  public PsiElement getColon() {
    return findChildByType(COLON);
  }

  @Override
  @Nullable
  public PsiElement getDotdotdot() {
    return findChildByType(DOTDOTDOT);
  }

  @Override
  @Nullable
  public PsiElement getExtern() {
    return findChildByType(EXTERN);
  }

  @Override
  @Nullable
  public PsiElement getFn() {
    return findChildByType(FN);
  }

  @Override
  @Nullable
  public PsiElement getFor() {
    return findChildByType(FOR);
  }

  @Override
  @Nullable
  public PsiElement getGt() {
    return findChildByType(GT);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getLt() {
    return findChildByType(LT);
  }

  @Override
  @Nullable
  public PsiElement getPub() {
    return findChildByType(PUB);
  }

  @Override
  @Nullable
  public PsiElement getStatic() {
    return findChildByType(STATIC);
  }

  @Override
  @Nullable
  public PsiElement getTypeof() {
    return findChildByType(TYPEOF);
  }

  @Override
  @Nullable
  public PsiElement getUnderscore() {
    return findChildByType(UNDERSCORE);
  }

  @Override
  @Nullable
  public PsiElement getUnsafe() {
    return findChildByType(UNSAFE);
  }

}
