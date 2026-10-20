package org.starfall.multigateway.shared.ui.icons.fluentui.regular

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

val FluentuiSystemIconsCheckmarkCircle: ImageVector
    get() {
        if (_FluentuiSystemIconsCheckmarkCircle != null) return _FluentuiSystemIconsCheckmarkCircle!!

        _FluentuiSystemIconsCheckmarkCircle = ImageVector.Builder(
            name = "checkmark-circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(17.5228f, 2f, 22f, 6.47715f, 22f, 12f)
                curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                curveTo(6.47715f, 22f, 2f, 17.5228f, 2f, 12f)
                curveTo(2f, 6.47715f, 6.47715f, 2f, 12f, 2f)
                close()
                moveTo(12f, 3.5f)
                curveTo(7.30558f, 3.5f, 3.5f, 7.30558f, 3.5f, 12f)
                curveTo(3.5f, 16.6944f, 7.30558f, 20.5f, 12f, 20.5f)
                curveTo(16.6944f, 20.5f, 20.5f, 16.6944f, 20.5f, 12f)
                curveTo(20.5f, 7.30558f, 16.6944f, 3.5f, 12f, 3.5f)
                close()
                moveTo(10.75f, 13.4393f)
                lineTo(15.2197f, 8.96967f)
                curveTo(15.5126f, 8.67678f, 15.9874f, 8.67678f, 16.2803f, 8.96967f)
                curveTo(16.5466f, 9.23594f, 16.5708f, 9.6526f, 16.3529f, 9.94621f)
                lineTo(16.2803f, 10.0303f)
                lineTo(11.2803f, 15.0303f)
                curveTo(11.0141f, 15.2966f, 10.5974f, 15.3208f, 10.3038f, 15.1029f)
                lineTo(10.2197f, 15.0303f)
                lineTo(7.71967f, 12.5303f)
                curveTo(7.42678f, 12.2374f, 7.42678f, 11.7626f, 7.71967f, 11.4697f)
                curveTo(7.98594f, 11.2034f, 8.4026f, 11.1792f, 8.69621f, 11.3971f)
                lineTo(8.78033f, 11.4697f)
                lineTo(10.75f, 13.4393f)
                lineTo(15.2197f, 8.96967f)
                lineTo(10.75f, 13.4393f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCheckmarkCircle!!
    }

private var _FluentuiSystemIconsCheckmarkCircle: ImageVector? = null
