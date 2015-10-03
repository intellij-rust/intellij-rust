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

public class RustRetTypeImpl extends RustCompositeElementImpl implements RustRetType {

  public RustRetTypeImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitRetType(this);
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
  @NotNull
  public List<RustGenericArgs> getGenericArgsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustGenericArgs.class);
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
  public List<RustRetType> getRetTypeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustRetType.class);
  }

  @Override
  @NotNull
  public List<RustTraitRef> getTraitRefList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTraitRef.class);
  }

  @Override
  @NotNull
  public List<RustTypeSum> getTypeSumList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeSum.class);
  }

  @Override
  @NotNull
  public List<RustTypeSums> getTypeSumsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeSums.class);
  }

  @Override
  @NotNull
  public PsiElement getArrow() {
    return findNotNullChildByType(ARROW);
  }

  @Override
  @Nullable
  public PsiElement getDotdotdot() {
    return findChildByType(DOTDOTDOT);
  }

  @Override
  @Nullable
  public PsiElement getExcl() {
    return findChildByType(EXCL);
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
  public PsiElement getSuper() {
    return findChildByType(SUPER);
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
