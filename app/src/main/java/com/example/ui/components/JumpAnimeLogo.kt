package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun JumpAnimeLogoIcon(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
    ) {
        val width = size.toPx()
        val height = size.toPx()
        
        // Solid Rounded Square background drawn with custom rotation
        // To make it look like the beautiful Anilab tilted square logo!
        rotate(12f) {
            val rectSize = width * 0.85f
            val offset = (width - rectSize) / 2f
            
            drawRoundRect(
                color = Color(0xFFFF2A43), // Vibrant Red
                topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                size = androidx.compose.ui.geometry.Size(rectSize, rectSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rectSize * 0.28f, rectSize * 0.28f)
            )
            
            // Draw a stylized white overlapping letter "J" & "A" or abstract stripes representing "JA"
            // For Anilab look, let's draw two bold white slanted lines forming a futuristic play logo or letter symbol
            val stripeWidth = rectSize * 0.16f
            val path = Path().apply {
                // Left slash
                moveTo(offset + rectSize * 0.25f, offset + rectSize * 0.75f)
                lineTo(offset + rectSize * 0.45f, offset + rectSize * 0.25f)
                lineTo(offset + rectSize * 0.60f, offset + rectSize * 0.25f)
                lineTo(offset + rectSize * 0.40f, offset + rectSize * 0.75f)
                close()
                
                // Right slash forming an elegant "A" with the left
                moveTo(offset + rectSize * 0.42f, offset + rectSize * 0.75f)
                lineTo(offset + rectSize * 0.68f, offset + rectSize * 0.35f)
                lineTo(offset + rectSize * 0.80f, offset + rectSize * 0.35f)
                lineTo(offset + rectSize * 0.54f, offset + rectSize * 0.75f)
                close()
                
                // Connection bar
                moveTo(offset + rectSize * 0.40f, offset + rectSize * 0.55f)
                lineTo(offset + rectSize * 0.64f, offset + rectSize * 0.55f)
                lineTo(offset + rectSize * 0.61f, offset + rectSize * 0.62f)
                lineTo(offset + rectSize * 0.38f, offset + rectSize * 0.62f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
fun JumpAnimeLogo(
    iconSize: Dp = 32.dp,
    textSize: Float = 22f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        JumpAnimeLogoIcon(size = iconSize)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "JumpAnime",
            fontSize = textSize.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
fun DotsCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF2A43),
    size: Dp = 44.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_angle"
    )
    
    Canvas(modifier = modifier.size(size)) {
        rotate(angle) {
            val numDots = 8
            val radius = size.toPx() / 2f
            val dotRadius = radius * 0.15f
            
            for (i in 0 until numDots) {
                val dotAngle = (i * (360f / numDots)) * (3.1415926535f / 180f)
                val x = radius + (radius - dotRadius * 2.5f) * kotlin.math.cos(dotAngle.toDouble()).toFloat()
                val y = radius + (radius - dotRadius * 2.5f) * kotlin.math.sin(dotAngle.toDouble()).toFloat()
                
                val alpha = (i + 1f) / numDots
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun JumpAnimeSplashScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)), // Match DarkBackground
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            JumpAnimeLogoIcon(
                size = 110.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "jumpanime",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            DotsCircularProgressIndicator(
                size = 44.dp,
                color = Color(0xFFFF2A43)
            )
        }
    }
}
