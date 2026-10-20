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

val FluentuiSystemIconsEyeOff: ImageVector
    get() {
        if (_FluentuiSystemIconsEyeOff != null) return _FluentuiSystemIconsEyeOff!!

        _FluentuiSystemIconsEyeOff = ImageVector.Builder(
            name = "eye-off",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(2.21967f, 2.21967f)
                curveTo(1.9534f, 2.48594f, 1.9292f, 2.9026f, 2.14705f, 3.19621f)
                lineTo(2.21967f, 3.28033f)
                lineTo(6.25424f, 7.3149f)
                curveTo(4.33225f, 8.66437f, 2.89577f, 10.6799f, 2.29888f, 13.0644f)
                curveTo(2.1983f, 13.4662f, 2.4425f, 13.8735f, 2.84431f, 13.9741f)
                curveTo(3.24613f, 14.0746f, 3.6534f, 13.8305f, 3.75399f, 13.4286f)
                curveTo(4.28346f, 11.3135f, 5.59112f, 9.53947f, 7.33416f, 8.39452f)
                lineTo(9.14379f, 10.2043f)
                curveTo(8.43628f, 10.9258f, 8f, 11.9143f, 8f, 13.0046f)
                curveTo(8f, 15.2138f, 9.79086f, 17.0046f, 12f, 17.0046f)
                curveTo(13.0904f, 17.0046f, 14.0788f, 16.5683f, 14.8004f, 15.8608f)
                lineTo(20.7197f, 21.7803f)
                curveTo(21.0126f, 22.0732f, 21.4874f, 22.0732f, 21.7803f, 21.7803f)
                curveTo(22.0466f, 21.5141f, 22.0708f, 21.0974f, 21.8529f, 20.8038f)
                lineTo(21.7803f, 20.7197f)
                lineTo(15.6668f, 14.6055f)
                lineTo(15.668f, 14.604f)
                lineTo(14.4679f, 13.4061f)
                lineTo(11.598f, 10.5368f)
                lineTo(11.6f, 10.536f)
                lineTo(8.71877f, 7.65782f)
                lineTo(8.72f, 7.656f)
                lineTo(7.58672f, 6.52549f)
                lineTo(3.28033f, 2.21967f)
                curveTo(2.98744f, 1.92678f, 2.51256f, 1.92678f, 2.21967f, 2.21967f)
                close()
                moveTo(10.2041f, 11.2655f)
                lineTo(13.7392f, 14.8006f)
                curveTo(13.2892f, 15.2364f, 12.6759f, 15.5046f, 12f, 15.5046f)
                curveTo(10.6193f, 15.5046f, 9.5f, 14.3853f, 9.5f, 13.0046f)
                curveTo(9.5f, 12.3287f, 9.76824f, 11.7154f, 10.2041f, 11.2655f)
                close()
                moveTo(12f, 5.5f)
                curveTo(10.9997f, 5.5f, 10.0291f, 5.64807f, 9.11109f, 5.925f)
                lineTo(10.3481f, 7.16119f)
                curveTo(10.8839f, 7.05532f, 11.4364f, 7f, 12f, 7f)
                curveTo(15.9231f, 7f, 19.3099f, 9.68026f, 20.2471f, 13.4332f)
                curveTo(20.3475f, 13.835f, 20.7546f, 14.0794f, 21.1565f, 13.9791f)
                curveTo(21.5584f, 13.8787f, 21.8028f, 13.4716f, 21.7024f, 13.0697f)
                curveTo(20.5994f, 8.65272f, 16.6155f, 5.5f, 12f, 5.5f)
                close()
                moveTo(12.1947f, 9.00928f)
                lineTo(15.996f, 12.81f)
                curveTo(15.8942f, 10.7531f, 14.2472f, 9.10764f, 12.1947f, 9.00928f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsEyeOff!!
    }

private var _FluentuiSystemIconsEyeOff: ImageVector? = null
