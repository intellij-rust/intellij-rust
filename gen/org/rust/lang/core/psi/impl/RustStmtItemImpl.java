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

public class RustStmtItemImpl extends RustNamedElementImpl implements RustStmtItem {

  public RustStmtItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitStmtItem(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustBlockItem getBlockItem() {
    return findChildByClass(RustBlockItem.class);
  }

  @Override
  @Nullable
  public RustConstItem getConstItem() {
    return findChildByClass(RustConstItem.class);
  }

  @Override
  @Nullable
  public RustExternCrateDecl getExternCrateDecl() {
    return findChildByClass(RustExternCrateDecl.class);
  }

  @Override
  @Nullable
  public RustExternFnItem getExternFnItem() {
    return findChildByClass(RustExternFnItem.class);
  }

  @Override
  @Nullable
  public RustStaticItem getStaticItem() {
    return findChildByClass(RustStaticItem.class);
  }

  @Override
  @Nullable
  public RustTypeItem getTypeItem() {
    return findChildByClass(RustTypeItem.class);
  }

  @Override
  @Nullable
  public RustUseItem getUseItem() {
    return findChildByClass(RustUseItem.class);
  }

}
