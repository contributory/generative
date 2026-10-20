package org.starfall.generative.shared.ui.icons.fluentui.regular

/*
MIT License

Copyright (c) 2020 Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FluentuiSystemIconsAttach: ImageVector
    get() {
        if (_FluentuiSystemIconsAttach != null) return _FluentuiSystemIconsAttach!!

        _FluentuiSystemIconsAttach = ImageVector.Builder(
            name = "attach",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(11.7722f, 3.74407f)
                curveTo(14.1136f, 1.40049f, 17.9126f, 1.40049f, 20.2558f, 3.74363f)
                curveTo(22.5388f, 6.0267f, 22.5974f, 9.69191f, 20.4314f, 12.0458f)
                lineTo(20.2432f, 12.2432f)
                lineTo(11.4432f, 21.0414f)
                lineTo(11.4066f, 21.0717f)
                curveTo(9.94541f, 22.3884f, 7.69141f, 22.3437f, 6.28404f, 20.9363f)
                curveTo(4.96502f, 19.6173f, 4.8429f, 17.5546f, 5.91769f, 16.0979f)
                curveTo(5.94103f, 16.0525f, 5.96928f, 16.0088f, 6.00249f, 15.9677f)
                lineTo(6.05605f, 15.908f)
                lineTo(6.14295f, 15.8203f)
                lineTo(6.28404f, 15.6724f)
                lineTo(6.28695f, 15.6753f)
                lineTo(13.7227f, 8.22096f)
                curveTo(13.9886f, 7.95434f, 14.4052f, 7.92958f, 14.6991f, 8.14704f)
                lineTo(14.7834f, 8.21955f)
                curveTo(15.05f, 8.48546f, 15.0747f, 8.90209f, 14.8573f, 9.19599f)
                lineTo(14.7848f, 9.28021f)
                lineTo(7.19015f, 16.8933f)
                curveTo(6.47251f, 17.7689f, 6.52239f, 19.0632f, 7.33979f, 19.8806f)
                curveTo(8.16885f, 20.7096f, 9.4885f, 20.7491f, 10.3643f, 19.999f)
                lineTo(19.197f, 11.1685f)
                curveTo(20.9525f, 9.41089f, 20.9525f, 6.56165f, 19.1951f, 4.80429f)
                curveTo(17.4927f, 3.10185f, 14.7655f, 3.04865f, 12.999f, 4.64469f)
                lineTo(12.8312f, 4.80429f)
                lineTo(12.8186f, 4.8186f)
                lineTo(3.28228f, 14.3549f)
                curveTo(2.98939f, 14.6478f, 2.51452f, 14.6478f, 2.22162f, 14.3549f)
                curveTo(1.95536f, 14.0887f, 1.93115f, 13.672f, 2.149f, 13.3784f)
                lineTo(2.22162f, 13.2943f)
                lineTo(11.7705f, 3.74363f)
                lineTo(11.7722f, 3.74407f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsAttach!!
    }

private var _FluentuiSystemIconsAttach: ImageVector? = null
