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

public class RustBindingImpl extends RustCompositeElementImpl implements RustBinding {

  public RustBindingImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitBinding(this);
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
  @Nullable
  public RustAnonParams getAnonParams() {
    return findChildByClass(RustAnonParams.class);
  }

  @Override
  @NotNull
  public List<RustBindings> getBindingsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustBindings.class);
  }

  @Override
  @Nullable
  public RustBounds getBounds() {
    return findChildByClass(RustBounds.class);
  }

  @Override
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
  }

  @Override
  @Nullable
  public RustFnParams getFnParams() {
    return findChildByClass(RustFnParams.class);
  }

  @Override
  @Nullable
  public RustGenericParams getGenericParams() {
    return findChildByClass(RustGenericParams.class);
  }

  @Override
  @NotNull
  public List<RustLifetimes> getLifetimesList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustLifetimes.class);
  }

  @Override
  @NotNull
  public List<RustRetType> getRetTypeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustRetType.class);
  }

  @Override
  @Nullable
  public RustTraitRef getTraitRef() {
    return findChildByClass(RustTraitRef.class);
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
  public PsiElement getBox() {
    return findChildByType(BOX);
  }

  @Override
  @Nullable
  public PsiElement getConst() {
    return findChildByType(CONST);
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
  @NotNull
  public PsiElement getEq() {
    return findNotNullChildByType(EQ);
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
  public PsiElement getLbrack() {
    return findChildByType(LBRACK);
  }

  @Override
  @Nullable
  public PsiElement getLifetime() {
    return findChildByType(LIFETIME);
  }

  @Override
  @Nullable
  public PsiElement getMul() {
    return findChildByType(MUL);
  }

  @Override
  @Nullable
  public PsiElement getMut() {
    return findChildByType(MUT);
  }

  @Override
  @Nullable
  public PsiElement getOror() {
    return findChildByType(OROR);
  }

  @Override
  @Nullable
  public PsiElement getProc() {
    return findChildByType(PROC);
  }

  @Override
  @Nullable
  public PsiElement getRbrack() {
    return findChildByType(RBRACK);
  }

  @Override
  @Nullable
  public PsiElement getSelf() {
    return findChildByType(SELF);
  }

  @Override
  @Nullable
  public PsiElement getSemicolon() {
    return findChildByType(SEMICOLON);
  }

  @Override
  @Nullable
  public PsiElement getStaticLifetime() {
    return findChildByType(STATIC_LIFETIME);
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
