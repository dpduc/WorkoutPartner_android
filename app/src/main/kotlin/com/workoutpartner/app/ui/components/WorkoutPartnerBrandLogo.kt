package com.workoutpartner.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workoutpartner.app.R
import com.workoutpartner.app.ui.theme.ElectricOrange

enum class LogoOrientation {
    VERTICAL,
    HORIZONTAL,
}

@Composable
fun WorkoutPartnerBrandLogo(
    modifier: Modifier = Modifier,
    orientation: LogoOrientation = LogoOrientation.VERTICAL,
    size: Dp = if (orientation == LogoOrientation.VERTICAL) 180.dp else 40.dp,
    showText: Boolean = true,
) {
    when (orientation) {
        LogoOrientation.VERTICAL -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_main),
                    contentDescription = "Workout Partner Main Logo",
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        LogoOrientation.HORIZONTAL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_main),
                    contentDescription = "Workout Partner Logo Mark",
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (showText) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "WORKOUT",
                            color = ElectricOrange,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.2.sp,
                            lineHeight = 15.sp,
                        )
                        Text(
                            text = "PARTNER",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            lineHeight = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
