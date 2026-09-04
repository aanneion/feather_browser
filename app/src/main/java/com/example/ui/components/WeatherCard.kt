package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.weather.*
import kotlin.math.roundToInt

@Composable
fun WeatherCard(
    state: WeatherUiState,
    isFahrenheit: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = FeatherShapes.LargeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_card")
    ) {
        Box(modifier = Modifier.padding(FeatherDimensions.SpacingMedium)) {
            when (state) {
                is WeatherUiState.Loading -> {
                    WeatherLoadingView()
                }
                is WeatherUiState.Success -> {
                    WeatherSuccessView(
                        weather = state.weather,
                        isFahrenheit = isFahrenheit,
                        isOffline = false,
                        onRefresh = onRefresh
                    )
                }
                is WeatherUiState.Offline -> {
                    if (state.weather != null) {
                        WeatherSuccessView(
                            weather = state.weather,
                            isFahrenheit = isFahrenheit,
                            isOffline = true,
                            onRefresh = onRefresh
                        )
                    } else {
                        WeatherErrorView(
                            message = "Weather unavailable offline",
                            onRetry = onRefresh
                        )
                    }
                }
                is WeatherUiState.Error -> {
                    WeatherErrorView(
                        message = state.message,
                        onRetry = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherLoadingView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FeatherDimensions.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(FeatherDimensions.SpacingMedium))
        Column {
            Text(
                text = "Locating your area...",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Fetching IP-based weather forecast",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeatherSuccessView(
    weather: WeatherData,
    isFahrenheit: Boolean,
    isOffline: Boolean,
    onRefresh: () -> Unit
) {
    val tempDisplay = if (isFahrenheit) {
        val f = (weather.temperatureCelsius * 9 / 5) + 32
        "${f.roundToInt()}°F"
    } else {
        "${weather.temperatureCelsius.roundToInt()}°C"
    }

    val feelsLikeDisplay = weather.feelsLikeCelsius?.let {
        if (isFahrenheit) {
            val f = (it * 9 / 5) + 32
            "${f.roundToInt()}°"
        } else {
            "${it.roundToInt()}°"
        }
    }

    val (iconVector, iconTint) = getWeatherIconAndTint(weather.weatherIcon)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Location & Refresh row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (weather.countryName.isNotBlank()) "${weather.locationName}, ${weather.countryName}" else weather.locationName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh weather",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Big Weather stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Weather icon circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = weather.condition,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = tempDisplay,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = weather.condition,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Secondary metrics
            Column(horizontalAlignment = Alignment.End) {
                if (feelsLikeDisplay != null) {
                    Text(
                        text = "Feels like $feelsLikeDisplay",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (weather.humidityPercent != null) {
                    Text(
                        text = "Humidity ${weather.humidityPercent}%",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (weather.windSpeedKmh != null) {
                    Text(
                        text = "Wind ${weather.windSpeedKmh.roundToInt()} km/h",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isOffline) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Cached",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Showing cached forecast",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun WeatherErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Weather unavailable",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text("Retry", fontSize = 12.sp)
        }
    }
}

private fun getWeatherIconAndTint(icon: WeatherIcon): Pair<ImageVector, Color> {
    return when (icon) {
        WeatherIcon.CLEAR_DAY -> Pair(Icons.Default.WbSunny, WeatherSunnyGold)
        WeatherIcon.CLEAR_NIGHT -> Pair(Icons.Default.NightsStay, Color(0xFF818CF8))
        WeatherIcon.PARTLY_CLOUDY_DAY -> Pair(Icons.Default.WbCloudy, WeatherCloudBlue)
        WeatherIcon.PARTLY_CLOUDY_NIGHT -> Pair(Icons.Default.NightsStay, Color(0xFF94A3B8))
        WeatherIcon.CLOUDY -> Pair(Icons.Default.Cloud, Color(0xFF94A3B8))
        WeatherIcon.RAIN -> Pair(Icons.Default.WaterDrop, WeatherRainIndigo)
        WeatherIcon.THUNDERSTORM -> Pair(Icons.Default.FlashOn, Color(0xFFFBBF24))
        WeatherIcon.SNOW -> Pair(Icons.Default.AcUnit, Color(0xFF67E8F9))
        WeatherIcon.MIST_OR_FOG -> Pair(Icons.Default.Deblur, Color(0xFF94A3B8))
    }
}
