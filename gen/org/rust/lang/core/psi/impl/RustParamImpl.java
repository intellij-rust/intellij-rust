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

public class RustParamImpl extends RustCompositeElementImpl implements RustParam {

  public RustParamImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitParam(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustAbi> getAbiList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustAbi.class);
  }

  @Override
  @NotNull
  public List<RustAnonParam> getAnonParamList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustAnonParam.class);
  }

  @Override
  @NotNull
  public List<RustAnonParams> getAnonParamsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustAnonParams.class);
  }

  @Override
  @NotNull
  public List<RustBounds> getBoundsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustBounds.class);
  }

  @Override
  @NotNull
  public List<RustExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustExpr.class);
  }

  @Override
  @NotNull
  public List<RustFnParams> getFnParamsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustFnParams.class);
  }

  @Override
  @NotNull
  public List<RustGenericArgs> getGenericArgsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustGenericArgs.class);
  }

  @Override
  @NotNull
  public List<RustGenericParams> getGenericParamsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustGenericParams.class);
  }

  @Override
  @NotNull
  public List<RustLifetimes> getLifetimesList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustLifetimes.class);
  }

  @Override
  @NotNull
  public RustPat getPat() {
    return findNotNullChildByClass(RustPat.class);
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
  public List<RustTypeParamBounds> getTypeParamBoundsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeParamBounds.class);
  }

  @Override
  @NotNull
  public PsiElement getColon() {
    return findNotNullChildByType(COLON);
  }

}
