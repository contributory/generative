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

val FluentuiSystemIconsTextBulletList: ImageVector
    get() {
        if (_FluentuiSystemIconsTextBulletList != null) return _FluentuiSystemIconsTextBulletList!!

        _FluentuiSystemIconsTextBulletList = ImageVector.Builder(
            name = "text-bullet-list",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.24929f, 17.5f)
                curveTo(3.93925f, 17.5f, 4.49858f, 18.0593f, 4.49858f, 18.7493f)
                curveTo(4.49858f, 19.4393f, 3.93925f, 19.9986f, 3.24929f, 19.9986f)
                curveTo(2.55933f, 19.9986f, 2f, 19.4393f, 2f, 18.7493f)
                curveTo(2f, 18.0593f, 2.55933f, 17.5f, 3.24929f, 17.5f)
                close()
                moveTo(6.75f, 18f)
                horizontalLineTo(21.25f)
                curveTo(21.6642f, 18f, 22f, 18.3358f, 22f, 18.75f)
                curveTo(22f, 19.1297f, 21.7178f, 19.4435f, 21.3518f, 19.4932f)
                lineTo(21.25f, 19.5f)
                horizontalLineTo(6.75f)
                curveTo(6.33579f, 19.5f, 6f, 19.1642f, 6f, 18.75f)
                curveTo(6f, 18.3703f, 6.28215f, 18.0565f, 6.64823f, 18.0068f)
                lineTo(6.75f, 18f)
                horizontalLineTo(21.25f)
                horizontalLineTo(6.75f)
                close()
                moveTo(3.24929f, 11f)
                curveTo(3.93925f, 11f, 4.49858f, 11.5593f, 4.49858f, 12.2493f)
                curveTo(4.49858f, 12.9393f, 3.93925f, 13.4986f, 3.24929f, 13.4986f)
                curveTo(2.55933f, 13.4986f, 2f, 12.9393f, 2f, 12.2493f)
                curveTo(2f, 11.5593f, 2.55933f, 11f, 3.24929f, 11f)
                close()
                moveTo(6.75f, 11.5f)
                horizontalLineTo(21.25f)
                curveTo(21.6642f, 11.5f, 22f, 11.8358f, 22f, 12.25f)
                curveTo(22f, 12.6297f, 21.7178f, 12.9435f, 21.3518f, 12.9932f)
                lineTo(21.25f, 13f)
                horizontalLineTo(6.75f)
                curveTo(6.33579f, 13f, 6f, 12.6642f, 6f, 12.25f)
                curveTo(6f, 11.8703f, 6.28215f, 11.5565f, 6.64823f, 11.5068f)
                lineTo(6.75f, 11.5f)
                horizontalLineTo(21.25f)
                horizontalLineTo(6.75f)
                close()
                moveTo(3.24929f, 4.5f)
                curveTo(3.93925f, 4.5f, 4.49858f, 5.05933f, 4.49858f, 5.74929f)
                curveTo(4.49858f, 6.43925f, 3.93925f, 6.99858f, 3.24929f, 6.99858f)
                curveTo(2.55933f, 6.99858f, 2f, 6.43925f, 2f, 5.74929f)
                curveTo(2f, 5.05933f, 2.55933f, 4.5f, 3.24929f, 4.5f)
                close()
                moveTo(6.75f, 5f)
                horizontalLineTo(21.25f)
                curveTo(21.6642f, 5f, 22f, 5.33579f, 22f, 5.75f)
                curveTo(22f, 6.1297f, 21.7178f, 6.44349f, 21.3518f, 6.49315f)
                lineTo(21.25f, 6.5f)
                horizontalLineTo(6.75f)
                curveTo(6.33579f, 6.5f, 6f, 6.16421f, 6f, 5.75f)
                curveTo(6f, 5.3703f, 6.28215f, 5.05651f, 6.64823f, 5.00685f)
                lineTo(6.75f, 5f)
                horizontalLineTo(21.25f)
                horizontalLineTo(6.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsTextBulletList!!
    }

private var _FluentuiSystemIconsTextBulletList: ImageVector? = null
