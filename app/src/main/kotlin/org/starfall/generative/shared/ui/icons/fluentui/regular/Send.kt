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

val FluentuiSystemIconsSend: ImageVector
    get() {
        if (_FluentuiSystemIconsSend != null) return _FluentuiSystemIconsSend!!

        _FluentuiSystemIconsSend = ImageVector.Builder(
            name = "send",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(5.69362f, 11.9997f)
                lineTo(2.29933f, 3.2715f)
                curveTo(2.0631f, 2.66403f, 2.65544f, 2.08309f, 3.2414f, 2.28959f)
                lineTo(3.33375f, 2.32885f)
                lineTo(21.3337f, 11.3288f)
                curveTo(21.852f, 11.588f, 21.8844f, 12.2975f, 21.4309f, 12.6129f)
                lineTo(21.3337f, 12.6705f)
                lineTo(3.33375f, 21.6705f)
                curveTo(2.75077f, 21.962f, 2.11746f, 21.426f, 2.2688f, 20.8234f)
                lineTo(2.29933f, 20.7278f)
                lineTo(5.69362f, 11.9997f)
                lineTo(2.29933f, 3.2715f)
                lineTo(5.69362f, 11.9997f)
                close()
                moveTo(4.4021f, 4.54007f)
                lineTo(7.01109f, 11.2491f)
                lineTo(13.6387f, 11.2497f)
                curveTo(14.0184f, 11.2497f, 14.3322f, 11.5318f, 14.3818f, 11.8979f)
                lineTo(14.3887f, 11.9997f)
                curveTo(14.3887f, 12.3794f, 14.1065f, 12.6932f, 13.7404f, 12.7428f)
                lineTo(13.6387f, 12.7497f)
                lineTo(7.01109f, 12.7491f)
                lineTo(4.4021f, 19.4593f)
                lineTo(19.3213f, 11.9997f)
                lineTo(4.4021f, 4.54007f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsSend!!
    }

private var _FluentuiSystemIconsSend: ImageVector? = null
