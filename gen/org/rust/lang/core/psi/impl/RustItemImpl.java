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

public class RustItemImpl extends RustCompositeElementImpl implements RustItem {

  public RustItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitItem(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustAttr> getAttrList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustAttr.class);
  }

  @Override
  @Nullable
  public RustConstItem getConstItem() {
    return findChildByClass(RustConstItem.class);
  }

  @Override
  @NotNull
  public List<RustDoc> getDocList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustDoc.class);
  }

  @Override
  @Nullable
  public RustEnumItem getEnumItem() {
    return findChildByClass(RustEnumItem.class);
  }

  @Override
  @Nullable
  public RustExternBlock getExternBlock() {
    return findChildByClass(RustExternBlock.class);
  }

  @Override
  @Nullable
  public RustFnItem getFnItem() {
    return findChildByClass(RustFnItem.class);
  }

  @Override
  @Nullable
  public RustImplItem getImplItem() {
    return findChildByClass(RustImplItem.class);
  }

  @Override
  @Nullable
  public RustModItem getModItem() {
    return findChildByClass(RustModItem.class);
  }

  @Override
  @Nullable
  public RustStaticItem getStaticItem() {
    return findChildByClass(RustStaticItem.class);
  }

  @Override
  @Nullable
  public RustStructItem getStructItem() {
    return findChildByClass(RustStructItem.class);
  }

  @Override
  @Nullable
  public RustTraitItem getTraitItem() {
    return findChildByClass(RustTraitItem.class);
  }

  @Override
  @Nullable
  public RustTypeItem getTypeItem() {
    return findChildByClass(RustTypeItem.class);
  }

  @Override
  @Nullable
  public PsiElement getPriv() {
    return findChildByType(PRIV);
  }

  @Override
  @Nullable
  public PsiElement getPub() {
    return findChildByType(PUB);
  }

}
