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

val FluentuiSystemIconsEdit: ImageVector
    get() {
        if (_FluentuiSystemIconsEdit != null) return _FluentuiSystemIconsEdit!!

        _FluentuiSystemIconsEdit = ImageVector.Builder(
            name = "edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(20.9519f, 3.0481f)
                curveTo(19.5543f, 1.65058f, 17.2885f, 1.65064f, 15.8911f, 3.04825f)
                lineTo(3.94103f, 14.9997f)
                curveTo(3.5347f, 15.4061f, 3.2491f, 15.9172f, 3.116f, 16.4762f)
                lineTo(2.02041f, 21.0777f)
                curveTo(1.96009f, 21.3311f, 2.03552f, 21.5976f, 2.21968f, 21.7817f)
                curveTo(2.40385f, 21.9659f, 2.67037f, 22.0413f, 2.92373f, 21.981f)
                lineTo(7.52498f, 20.8855f)
                curveTo(8.08418f, 20.7523f, 8.59546f, 20.4666f, 9.00191f, 20.0601f)
                lineTo(20.952f, 8.10861f)
                curveTo(22.3493f, 6.71112f, 22.3493f, 4.4455f, 20.9519f, 3.0481f)
                close()
                moveTo(16.9518f, 4.10884f)
                curveTo(17.7634f, 3.29709f, 19.0795f, 3.29705f, 19.8912f, 4.10876f)
                curveTo(20.7028f, 4.9204f, 20.7029f, 6.23632f, 19.8913f, 7.04801f)
                lineTo(19f, 7.93946f)
                lineTo(16.0606f, 5.00012f)
                lineTo(16.9518f, 4.10884f)
                close()
                moveTo(15f, 6.06084f)
                lineTo(17.9394f, 9.00018f)
                lineTo(7.94119f, 18.9995f)
                curveTo(7.73104f, 19.2097f, 7.46668f, 19.3574f, 7.17755f, 19.4263f)
                lineTo(3.76191f, 20.2395f)
                lineTo(4.57521f, 16.8237f)
                curveTo(4.64402f, 16.5346f, 4.79168f, 16.2704f, 5.00175f, 16.0603f)
                lineTo(15f, 6.06084f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsEdit!!
    }

private var _FluentuiSystemIconsEdit: ImageVector? = null
