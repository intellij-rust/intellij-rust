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

public class RustForExprImpl extends RustExprImpl implements RustForExpr {

  public RustForExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitForExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public RustBlock getBlock() {
    return findNotNullChildByClass(RustBlock.class);
  }

  @Override
  @NotNull
  public RustPat getPat() {
    return findNotNullChildByClass(RustPat.class);
  }

  @Override
  @NotNull
  public PsiElement getColon() {
    return findNotNullChildByType(COLON);
  }

  @Override
  @NotNull
  public PsiElement getFor() {
    return findNotNullChildByType(FOR);
  }

  @Override
  @NotNull
  public PsiElement getIn() {
    return findNotNullChildByType(IN);
  }

  @Override
  @NotNull
  public PsiElement getLbrace() {
    return findNotNullChildByType(LBRACE);
  }

  @Override
  @Nullable
  public PsiElement getLifetime() {
    return findChildByType(LIFETIME);
  }

  @Override
  @NotNull
  public PsiElement getRbrace() {
    return findNotNullChildByType(RBRACE);
  }

  @Override
  @Nullable
  public PsiElement getStaticLifetime() {
    return findChildByType(STATIC_LIFETIME);
  }

}
