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

public class RustAnonParamImpl extends RustCompositeElementImpl implements RustAnonParam {

  public RustAnonParamImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitAnonParam(this);
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
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
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
  @Nullable
  public RustTypeSums getTypeSums() {
    return findChildByClass(RustTypeSums.class);
  }

  @Override
  @Nullable
  public PsiElement getBox() {
    return findChildByType(BOX);
  }

  @Override
  @Nullable
  public PsiElement getColon() {
    return findChildByType(COLON);
  }

  @Override
  @Nullable
  public PsiElement getComma() {
    return findChildByType(COMMA);
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
  public PsiElement getLparen() {
    return findChildByType(LPAREN);
  }

  @Override
  @Nullable
  public PsiElement getLt() {
    return findChildByType(LT);
  }

  @Override
  @Nullable
  public PsiElement getMul() {
    return findChildByType(MUL);
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
  public PsiElement getUnsafe() {
    return findChildByType(UNSAFE);
  }

}
