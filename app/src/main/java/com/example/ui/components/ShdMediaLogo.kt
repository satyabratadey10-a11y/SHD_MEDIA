package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.PrismMagenta
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple

@Composable
fun ShdMediaLogoBadge(
    size: Dp = 36.dp,
    cornerRadius: Dp = 10.dp,
    isCircular: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(PrismPurple, PrismMagenta, PrismPink, PrismOrange)
        )
    }
    val shape = if (isCircular) CircleShape else RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF1A0826))
            .border(width = 1.8.dp, brush = borderBrush, shape = shape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.shd_media_logo_1794041847902),
            contentDescription = "SHD Media Logo",
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    }
}
