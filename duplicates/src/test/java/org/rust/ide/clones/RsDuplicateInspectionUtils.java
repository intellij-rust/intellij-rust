package org.rust.ide.clones;

import com.jetbrains.clones.DuplicateInspection;
import com.jetbrains.clones.configuration.DuplicateInspectionConfiguration;
import com.jetbrains.clones.languagescope.DuplicateScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// https://youtrack.jetbrains.com/issue/KT-40132
public class RsDuplicateInspectionUtils {

    @Nullable
    public static DuplicateInspectionConfiguration findConfiguration(@NotNull DuplicateInspection inspection,
                                                                     @NotNull DuplicateScope scope) {
        return inspection.getState().findConfiguration(scope);
    }
}
