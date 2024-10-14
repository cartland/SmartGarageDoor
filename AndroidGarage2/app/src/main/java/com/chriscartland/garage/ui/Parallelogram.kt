package com.chriscartland.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class ParallelogramShape(
    private val angleDegrees: Float,
    private val squareLeft: Boolean = false,
    private val squareRight: Boolean = false,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(
            path = drawParallelogramPath(
                size,
                angleDegrees,
                squareLeft,
                squareRight,
            )
        )
    }
}

fun drawParallelogramPath(
    size: Size,
    angleDegrees: Float,
    squareLeft: Boolean = false,
    squareRight: Boolean = false,
): Path {
    val path = Path()
    val angleRadians = Math.toRadians(angleDegrees.toDouble())
    val offsetX = (size.height * Math.tan(angleRadians)).toFloat()

    path.moveTo(if (squareLeft) 0f else offsetX, 0f)
    path.lineTo(size.width, 0f)
    path.lineTo(size.width - if (squareRight) 0f else offsetX, size.height)
    path.lineTo(0f, size.height)
    path.close()

    return path
}

@Composable
fun ParallelogramProgressBar(
    max: Int,
    complete: Int,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    angleDegrees: Float = 30f,
    colorComplete: Color = Color(0xFF3333FF),
    colorIncomplete: Color = Color(0xFF333333),
) {
    Row(
        modifier = modifier,
    ) {
        for (i in 1..max) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(
                        ParallelogramShape(
                            angleDegrees = angleDegrees,
                            squareLeft = (i == 1),
                            squareRight = (i == max),
                        )
                    )
                    .background(
                        if (i <= complete) colorComplete else colorIncomplete,
                    )
            )
        }
    }
}

@Preview
@Composable
fun ParallelogramUI() {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ParallelogramProgressBar(
            max = 5,
            complete = 2,
            height = 20.dp,
            colorComplete = Color(0xFF4444FF),
            colorIncomplete = Color(0xFF444444),
        )
    }
}
