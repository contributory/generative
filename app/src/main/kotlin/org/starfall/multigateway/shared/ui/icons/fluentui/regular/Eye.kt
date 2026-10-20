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

val FluentuiSystemIconsEye: ImageVector
    get() {
        if (_FluentuiSystemIconsEye != null) return _FluentuiSystemIconsEye!!

        _FluentuiSystemIconsEye = ImageVector.Builder(
            name = "eye",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(11.9999f, 9.00462f)
                curveTo(14.209f, 9.00462f, 15.9999f, 10.7955f, 15.9999f, 13.0046f)
                curveTo(15.9999f, 15.2138f, 14.209f, 17.0046f, 11.9999f, 17.0046f)
                curveTo(9.79073f, 17.0046f, 7.99987f, 15.2138f, 7.99987f, 13.0046f)
                curveTo(7.99987f, 10.7955f, 9.79073f, 9.00462f, 11.9999f, 9.00462f)
                close()
                moveTo(11.9999f, 10.5046f)
                curveTo(10.6192f, 10.5046f, 9.49987f, 11.6239f, 9.49987f, 13.0046f)
                curveTo(9.49987f, 14.3853f, 10.6192f, 15.5046f, 11.9999f, 15.5046f)
                curveTo(13.3806f, 15.5046f, 14.4999f, 14.3853f, 14.4999f, 13.0046f)
                curveTo(14.4999f, 11.6239f, 13.3806f, 10.5046f, 11.9999f, 10.5046f)
                close()
                moveTo(11.9999f, 5.5f)
                curveTo(16.6134f, 5.5f, 20.596f, 8.65001f, 21.701f, 13.0644f)
                curveTo(21.8016f, 13.4662f, 21.5574f, 13.8735f, 21.1556f, 13.9741f)
                curveTo(20.7537f, 14.0746f, 20.3465f, 13.8305f, 20.2459f, 13.4286f)
                curveTo(19.307f, 9.67796f, 15.9212f, 7f, 11.9999f, 7f)
                curveTo(8.07681f, 7f, 4.68997f, 9.68026f, 3.75273f, 13.4332f)
                curveTo(3.65237f, 13.835f, 3.24523f, 14.0794f, 2.84336f, 13.9791f)
                curveTo(2.44149f, 13.8787f, 2.19707f, 13.4716f, 2.29743f, 13.0697f)
                curveTo(3.40052f, 8.65272f, 7.38436f, 5.5f, 11.9999f, 5.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsEye!!
    }

private var _FluentuiSystemIconsEye: ImageVector? = null
