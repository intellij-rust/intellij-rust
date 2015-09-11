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

public class RustPatImpl extends RustCompositeElementImpl implements RustPat {

  public RustPatImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitPat(this);
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
  @Nullable
  public RustBindingMode getBindingMode() {
    return findChildByClass(RustBindingMode.class);
  }

  @Override
  @NotNull
  public List<RustBindings> getBindingsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustBindings.class);
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
  public List<RustGenericParams> getGenericParamsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustGenericParams.class);
  }

  @Override
  @NotNull
  public List<RustLifetimes> getLifetimesList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustLifetimes.class);
  }

  @Override
  @Nullable
  public RustPat getPat() {
    return findChildByClass(RustPat.class);
  }

  @Override
  @Nullable
  public RustPatStruct getPatStruct() {
    return findChildByClass(RustPatStruct.class);
  }

  @Override
  @Nullable
  public RustPatTup getPatTup() {
    return findChildByClass(RustPatTup.class);
  }

  @Override
  @Nullable
  public RustPatVec getPatVec() {
    return findChildByClass(RustPatVec.class);
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
  @Nullable
  public PsiElement getAnd() {
    return findChildByType(AND);
  }

  @Override
  @Nullable
  public PsiElement getAndand() {
    return findChildByType(ANDAND);
  }

  @Override
  @Nullable
  public PsiElement getAt() {
    return findChildByType(AT);
  }

  @Override
  @Nullable
  public PsiElement getBox() {
    return findChildByType(BOX);
  }

  @Override
  @Nullable
  public PsiElement getComma() {
    return findChildByType(COMMA);
  }

  @Override
  @Nullable
  public PsiElement getDotdot() {
    return findChildByType(DOTDOT);
  }

  @Override
  @Nullable
  public PsiElement getDotdotdot() {
    return findChildByType(DOTDOTDOT);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getLbrace() {
    return findChildByType(LBRACE);
  }

  @Override
  @Nullable
  public PsiElement getLbrack() {
    return findChildByType(LBRACK);
  }

  @Override
  @Nullable
  public PsiElement getLparen() {
    return findChildByType(LPAREN);
  }

  @Override
  @Nullable
  public PsiElement getMut() {
    return findChildByType(MUT);
  }

  @Override
  @Nullable
  public PsiElement getRbrace() {
    return findChildByType(RBRACE);
  }

  @Override
  @Nullable
  public PsiElement getRbrack() {
    return findChildByType(RBRACK);
  }

  @Override
  @Nullable
  public PsiElement getRparen() {
    return findChildByType(RPAREN);
  }

  @Override
  @Nullable
  public PsiElement getUnderscore() {
    return findChildByType(UNDERSCORE);
  }

}
