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

public class RustTypeMethodImpl extends RustCompositeElementImpl implements RustTypeMethod {

  public RustTypeMethodImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitTypeMethod(this);
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
  @Nullable
  public RustAnonParams getAnonParams() {
    return findChildByClass(RustAnonParams.class);
  }

  @Override
  @NotNull
  public RustGenericParams getGenericParams() {
    return findNotNullChildByClass(RustGenericParams.class);
  }

  @Override
  @NotNull
  public List<RustOuterAttr> getOuterAttrList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustOuterAttr.class);
  }

  @Override
  @NotNull
  public RustRetType getRetType() {
    return findNotNullChildByClass(RustRetType.class);
  }

  @Override
  @Nullable
  public RustTypeAscription getTypeAscription() {
    return findChildByClass(RustTypeAscription.class);
  }

  @Override
  @Nullable
  public RustWhereClause getWhereClause() {
    return findChildByClass(RustWhereClause.class);
  }

  @Override
  @Nullable
  public PsiElement getAnd() {
    return findChildByType(AND);
  }

  @Override
  @Nullable
  public PsiElement getExtern() {
    return findChildByType(EXTERN);
  }

  @Override
  @NotNull
  public PsiElement getFn() {
    return findNotNullChildByType(FN);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getLifetime() {
    return findChildByType(LIFETIME);
  }

  @Override
  @NotNull
  public PsiElement getLparen() {
    return findNotNullChildByType(LPAREN);
  }

  @Override
  @Nullable
  public PsiElement getMut() {
    return findChildByType(MUT);
  }

  @Override
  @Nullable
  public PsiElement getPub() {
    return findChildByType(PUB);
  }

  @Override
  @NotNull
  public PsiElement getRparen() {
    return findNotNullChildByType(RPAREN);
  }

  @Override
  @Nullable
  public PsiElement getSelf() {
    return findChildByType(SELF);
  }

  @Override
  @NotNull
  public PsiElement getSemicolon() {
    return findNotNullChildByType(SEMICOLON);
  }

  @Override
  @Nullable
  public PsiElement getStaticLifetime() {
    return findChildByType(STATIC_LIFETIME);
  }

  @Override
  @Nullable
  public PsiElement getUnsafe() {
    return findChildByType(UNSAFE);
  }

}
