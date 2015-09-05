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

public class RustModItemImpl extends RustNamedElementImpl implements RustModItem {

  public RustModItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitModItem(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustExternCrateDecl> getExternCrateDeclList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustExternCrateDecl.class);
  }

  @Override
  @NotNull
  public List<RustItem> getItemList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustItem.class);
  }

  @Override
  @NotNull
  public List<RustUseDecl> getUseDeclList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustUseDecl.class);
  }

  @Override
  @Nullable
  public PsiElement getLbrace() {
    return findChildByType(LBRACE);
  }

  @Override
  @NotNull
  public PsiElement getMod() {
    return findNotNullChildByType(MOD);
  }

  @Override
  @Nullable
  public PsiElement getRbrace() {
    return findChildByType(RBRACE);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

}
