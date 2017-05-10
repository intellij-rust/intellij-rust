package org.rust.ide.idea;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// BACKCOMPAT: 2017.1
//https://github.com/JetBrains/intellij-community/commit/a09fc34ef231220b4eb1a091bd2a25bedb25d085#diff-6180cd1c5f27463e1236dfdea209a49cL51
public abstract class RsModuleTypeBase extends ModuleType<RsModuleBuilder> {
    protected RsModuleTypeBase(@NotNull String id) {
        super(id);
    }

    // @Override
    public Icon getBigIcon() {
        return null;
    }
}
