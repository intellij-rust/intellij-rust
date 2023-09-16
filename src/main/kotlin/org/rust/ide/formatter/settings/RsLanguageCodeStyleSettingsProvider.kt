/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.psi.codeStyle.*
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.*
import org.rust.RsBundle
import org.rust.lang.RsLanguage

class RsLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = RsLanguage

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
        RsCodeStyleSettings(settings)

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings
    ): CodeStyleConfigurable {
        return object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                RsCodeStyleMainPanel(currentSettings, settings)
        }
    }

    override fun getConfigurableDisplayName(): String = RsBundle.message("settings.rust.code.style.name")

    override fun getCodeSample(settingsType: SettingsType): String =
        when (settingsType) {
            INDENT_SETTINGS -> INDENT_SAMPLE
            SPACING_SETTINGS -> SPACING_SAMPLE
            WRAPPING_AND_BRACES_SETTINGS -> WRAPPING_AND_BRACES_SAMPLE
            BLANK_LINES_SETTINGS -> BLANK_LINES_SAMPLE
            else -> ""
        }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            BLANK_LINES_SETTINGS -> {
                consumer.showStandardOptions(
                    BlankLinesOption.KEEP_BLANK_LINES_IN_DECLARATIONS.name,
                    BlankLinesOption.KEEP_BLANK_LINES_IN_CODE.name)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS",
                    RsBundle.message("settings.rust.code.style.between.declarations"),
                    CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES)
            }

            SPACING_SETTINGS -> {
                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "SPACE_AROUND_ASSOC_TYPE_BINDING",
                    RsBundle.message("settings.rust.code.style.around.associated.type.bindings"),
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_IN_TYPE_PARAMETERS)
            }

            WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showStandardOptions(
                    WrappingOrBraceOption.KEEP_LINE_BREAKS.name,
                    WrappingOrBraceOption.RIGHT_MARGIN.name,
                    WrappingOrBraceOption.ALIGN_MULTILINE_CHAINED_METHODS.name,
                    WrappingOrBraceOption.ALIGN_MULTILINE_PARAMETERS.name,
                    WrappingOrBraceOption.ALIGN_MULTILINE_PARAMETERS_IN_CALLS.name)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "ALLOW_ONE_LINE_MATCH",
                    RsBundle.message("settings.rust.code.style.match.expressions.in.one.line"),
                    CodeStyleSettingsCustomizableOptions.getInstance().WRAPPING_KEEP)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "PRESERVE_PUNCTUATION",
                    RsBundle.message("settings.rust.code.style.punctuation"),
                    CodeStyleSettingsCustomizableOptions.getInstance().WRAPPING_KEEP)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "ALIGN_RET_TYPE",
                    RsBundle.message("settings.rust.code.style.align.return.type"),
                    CodeStyleSettingsCustomizableOptions.getInstance().WRAPPING_METHOD_PARAMETERS)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "ALIGN_WHERE_CLAUSE",
                    RsBundle.message("settings.rust.code.style.align.where.clause"),
                    CodeStyleSettingsCustomizableOptions.getInstance().WRAPPING_METHOD_PARAMETERS)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "ALIGN_TYPE_PARAMS",
                    ApplicationBundle.message("wrapping.align.when.multiline"),
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_IN_TYPE_PARAMETERS)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "INDENT_WHERE_CLAUSE",
                    RsBundle.message("settings.rust.code.style.indent.where.clause"),
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_IN_TYPE_PARAMETERS)

                consumer.showCustomOption(RsCodeStyleSettings::class.java,
                    "ALIGN_WHERE_BOUNDS",
                    RsBundle.message("settings.rust.code.style.align.where.clause.bounds"),
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_IN_TYPE_PARAMETERS)
            }

            COMMENTER_SETTINGS -> {
                consumer.showStandardOptions(
                    CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name,
                    CommenterOption.LINE_COMMENT_ADD_SPACE.name,
                    CommenterOption.BLOCK_COMMENT_AT_FIRST_COLUMN.name)
            }
            else -> Unit
        }
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        commonSettings.RIGHT_MARGIN = 100
        commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true

        // Make default behavior consistent with rustfmt
        commonSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
        commonSettings.LINE_COMMENT_ADD_SPACE = true
        commonSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false

        // FIXME(mkaput): It's a hack
        // Nobody else does this and still somehow achieve similar effect
        indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.INDENT_SIZE
    }
}

private fun sample(@org.intellij.lang.annotations.Language("Rust") code: String) = code.trim()

private val INDENT_SAMPLE = sample("""
struct Vector {
    x: f64,
    y: f64,
    z: f64
}

impl Vector {
    fn add(&self, other: &Vector) -> Vector {
        Vector {
            x: self.x + other.x,
            y: self.y + other.y,
            z: self.z + other.z,
        }
    }
}
""")

private val SPACING_SAMPLE = sample("""
trait Trait0<A, B, T: Trait1<A>> {
    type Output;
}

trait Trait1<T> {}

fn method<A, B, T, C>(value: T) where T: Trait0<A, B, T, Output=C> {}
""")


private val WRAPPING_AND_BRACES_SAMPLE = sample("""
fn concat<X, Y, I>(xs: X,
                   ys: Y)
                   -> Box<Iterator<Item=I>>
    where X: Iterator<Item=I>,
          Y: Iterator<Item=I>
{
    unimplemented!()
}


fn main() {
    let xs = vec![1, 2, 3].into_iter()
        .map(|x| x * 2)
        .filter(|x| x > 2);

    let ys = vec![1,
                  2,
                  3].into_iter();

    let zs = concat(xs,
                    ys);

    let is_even = match zs.next { Some(x) => x % 2 == 0, None => false, };

    match is_even {
        true => {
            // comment
        },
        _ => println("false"),
    }
    return
}
""")


private val BLANK_LINES_SAMPLE = sample("""
#![allow(dead_code)]



use std::cmp::{max, min};



struct Rectangle {
    p1: (i32, i32),



    p2: (i32, i32),
}



impl Rectangle {
    fn dimensions(&self) -> (i32, i32) {
        let (x1, y1) = self.p1;
        let (x2, y2) = self.p2;



        ((x1 - x2).abs(), (y1 - y2).abs())
    }



    fn area(&self) -> i32 {
        let (a, b) = self.dimensions();
        a * b
    }
}
""")
