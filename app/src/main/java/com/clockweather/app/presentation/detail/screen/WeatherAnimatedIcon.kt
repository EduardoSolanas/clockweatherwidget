package com.clockweather.app.presentation.detail.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.clockweather.app.domain.model.WeatherCondition
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun WeatherAnimatedIcon(condition: WeatherCondition, modifier: Modifier = Modifier) {
    when (condition) {
        WeatherCondition.CLEAR_DAY -> SunBackground(modifier)
        WeatherCondition.CLEAR_NIGHT -> MoonBackground(modifier)
        WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.PARTLY_CLOUDY_DAY -> PartlyCloudyBackground(modifier, true)
        WeatherCondition.MAINLY_CLEAR_NIGHT, WeatherCondition.PARTLY_CLOUDY_NIGHT -> PartlyCloudyBackground(modifier, false)
        WeatherCondition.OVERCAST -> OvercastBackground(modifier)
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> FogBackground(modifier)
        WeatherCondition.DRIZZLE_LIGHT, WeatherCondition.DRIZZLE_MODERATE,
        WeatherCondition.DRIZZLE_DENSE, WeatherCondition.FREEZING_DRIZZLE_LIGHT,
        WeatherCondition.FREEZING_DRIZZLE_HEAVY -> DrizzleBackground(modifier)
        WeatherCondition.RAIN_SLIGHT, WeatherCondition.RAIN_MODERATE,
        WeatherCondition.RAIN_SHOWER_SLIGHT, WeatherCondition.FREEZING_RAIN_LIGHT,
        WeatherCondition.FREEZING_RAIN_HEAVY -> RainBackground(modifier, false)
        WeatherCondition.RAIN_HEAVY, WeatherCondition.RAIN_SHOWER_MODERATE,
        WeatherCondition.RAIN_SHOWER_VIOLENT -> RainBackground(modifier, true)
        WeatherCondition.SNOW_SLIGHT, WeatherCondition.SNOW_MODERATE, WeatherCondition.SNOW_HEAVY,
        WeatherCondition.SNOW_GRAINS, WeatherCondition.SNOW_SHOWER_SLIGHT,
        WeatherCondition.SNOW_SHOWER_HEAVY -> SnowBackground(modifier)
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_SLIGHT_HAIL,
        WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> ThunderstormBackground(modifier)
        else -> SunBackground(modifier)
    }
}

// ─── Colours ──────────────────────────────────────────────────────────────────
private val SunYellow       = Color(0xFFFFD600)
private val SunOrange       = Color(0xFFFF5722)
private val SunCore         = Color(0xFFFFFFF0)
private val SunGlow         = Color(0x99FFD600)
private val CloudWhite      = Color(0xFFFFFFFF)
private val CloudShadow     = Color(0xFF90A4AE)
private val CloudStorm      = Color(0xFF37474F)
private val RainBlue        = Color(0xFF42A5F5)
private val SnowWhite       = Color(0xFFFAFAFA)
private val LightningYellow = Color(0xFFFFF59D)
private val MoonCream       = Color(0xFFFFF9C4)
private val MoonShadow      = Color(0xFF9575CD)
private val StarWhite       = Color(0xFFFFFFFF)
private val FogGray         = Color(0xFF78909C)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawCloud(center: Offset, size: Float, color: Color, alpha: Float = 1f) {
    val radius = size / 2
    val cloudColor = color.copy(alpha = alpha)
    
    withTransform({
        translate(center.x - radius, center.y - radius)
    }) {
        // High-det Cloud Path (6 organic bumps)
        val cloudPath = Path().apply {
            moveTo(radius * 0.5f, radius * 1.4f)
            // Left flourish
            cubicTo(radius * -0.3f, radius * 1.4f, radius * -0.4f, radius * 0.9f, radius * 0.1f, radius * 0.7f)
            // Top left bump
            cubicTo(radius * 0.0f, radius * 0.3f, radius * 0.4f, radius * 0.2f, radius * 0.6f, radius * 0.4f)
            // Main peak
            cubicTo(radius * 0.7f, radius * -0.1f, radius * 1.4f, radius * -0.1f, radius * 1.5f, radius * 0.5f)
            // Top right bump
            cubicTo(radius * 1.7f, radius * 0.3f, radius * 2.0f, radius * 0.4f, radius * 2.0f, radius * 0.7f)
            // Right flourish
            cubicTo(radius * 2.4f, radius * 0.9f, radius * 2.3f, radius * 1.4f, radius * 1.6f, radius * 1.4f)
            lineTo(radius * 0.5f, radius * 1.4f)
            close()
        }
        
        // 1. Ambient Drop Shadow (Soft depth)
        drawPath(
            path = cloudPath,
            color = Color.Black.copy(alpha = alpha * 0.12f),
            style = Fill,
            blendMode = BlendMode.Multiply
        )
        
        // 2. Base Body with Vertical Depth Gradient
        val bodyBrush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = alpha * 0.2f),
            0.4f to cloudColor,
            1f to cloudColor.copy(alpha = alpha * 0.8f),
            startY = 0f,
            endY = radius * 1.5f
        )
        drawPath(cloudPath, bodyBrush)
        
        // 3. Silver Lining / Sun Bloom
        val bloomBrush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha * 0.4f), Color.Transparent),
            center = Offset(radius * 0.8f, radius * 0.4f),
            radius = radius * 1.8f
        )
        drawPath(cloudPath, bloomBrush, blendMode = BlendMode.Screen)
        
        // 4. Volumetric Inner Bumps (Simulated thickness)
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.15f),
            radius = radius * 0.35f,
            center = Offset(radius * 0.6f, radius * 0.7f)
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.12f),
            radius = radius * 0.45f,
            center = Offset(radius * 1.3f, radius * 0.75f)
        )
        
        // 5. Rim Edge Highlight
        val rimPath = Path().apply {
            moveTo(radius * 0.2f, radius * 0.7f)
            cubicTo(radius * 0.2f, radius * 0.4f, radius * 0.5f, radius * 0.3f, radius * 0.6f, radius * 0.4f)
            cubicTo(radius * 0.7f, radius * 0.0f, radius * 1.4f, radius * 0.0f, radius * 1.5f, radius * 0.5f)
        }
        drawPath(rimPath, Color.White.copy(alpha = alpha * 0.35f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun rememberTime(): State<Long> {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (isActive) {
            time.longValue = System.currentTimeMillis() - startTime
            withFrameMillis { }
        }
    }
    return time
}

// ─── Particle System ─────────────────────────────────────────────────────────

private class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var alpha: Float,
    var size: Float,
    var angle: Float = 90f,
    var z: Float = 1f // For depth/parallax
)

@Composable
private fun rememberParticles(count: Int, spawnArea: Size): List<Particle> {
    return remember(count) {
        List(count) {
            val zDepth = 0.2f + Random.nextFloat() * 1.5f
            Particle(
                x = Random.nextFloat() * spawnArea.width,
                y = Random.nextFloat() * spawnArea.height,
                speed = (5f + Random.nextFloat() * 10f) * zDepth,
                alpha = (0.3f + Random.nextFloat() * 0.7f),
                size = (1f + Random.nextFloat() * 3f) * zDepth,
                z = zDepth
            )
        }
    }
}


// ─── Animations ─────────────────────────────────────────────────────────────

@Composable
fun SunBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "sun")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3000, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "pulse"
    )
    val coronaPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corona"
    )
    val time by rememberTime()
    val dustMotes = rememberParticles(25, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.22f
        val w = size.width
        val h = size.height

        // Intense Sun Bloom Corona
        drawCircle(
            Brush.radialGradient(listOf(SunYellow.copy(alpha = 0.6f / coronaPulse), Color.Transparent), center, radius * 3.5f * coronaPulse),
            radius * 3.5f * coronaPulse, center
        )
        drawCircle(
            Brush.radialGradient(listOf(SunOrange.copy(alpha = 0.4f), Color.Transparent), center, radius * 5f),
            radius * 5f, center
        )

        // Dynamic Rotating Rays with Heat Haze
        rotate(rotation, center) {
            for (i in 0 until 14) {
                val angle = i * (360f / 14f)
                val rayHeat = sin((time / 300f) + i) * 0.2f
                val length = radius * (1.8f + rayHeat)
                val rayWidth = radius * (0.08f + (rayHeat * 0.05f))
                
                rotate(angle, center) {
                    drawLine(
                        Brush.linearGradient(
                            listOf(SunOrange.copy(alpha = 0.8f), Color.Transparent),
                            start = Offset(center.x, center.y - radius * 1.1f),
                            end = Offset(center.x, center.y - length)
                        ),
                        Offset(center.x, center.y - radius * 1.1f),
                        Offset(center.x, center.y - length),
                        strokeWidth = rayWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Core Body
        drawCircle(
            Brush.radialGradient(listOf(Color.White, SunCore, SunYellow), center, radius * pulse),
            radius * pulse, center
        )
        
        // Solar Dust Motes (Floating particles catching sunlight)
        dustMotes.forEach { p ->
            val driftX = sin((time + p.y) / 2000f) * 30f * p.z
            val driftY = cos((time + p.x) / 1500f) * -20f * p.z
            val x = (p.x + driftX) % w
            val y = (p.y + driftY) % h
            // Distance from sun affects brightness
            val dist = kotlin.math.sqrt((x - center.x) * (x - center.x) + (y - center.y) * (y - center.y))
            val brightness = (1f - (dist / w).coerceIn(0f, 1f)) * p.alpha
            
            drawCircle(
                color = SunYellow.copy(alpha = brightness * 0.8f),
                radius = p.size * 1.2f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun MoonBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "moon")
    val moonPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(4000, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "pulse"
    )
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "stars"
    )
    val time by rememberTime()
    val shootingStars = rememberParticles(2, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.3f
        val w = size.width
        val h = size.height

        // AAA Ultra HD Aurora Borealis Effect
        val auroraShift = sin(time / 2500f) * 60f
        val auroraShift2 = cos(time / 1800f) * 40f
        
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E676).copy(alpha = 0.15f), Color.Transparent),
                startY = 0f,
                endY = h * 0.65f + auroraShift
            ),
            size = size,
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFD500F9).copy(alpha = 0.1f), Color.Transparent),
                startY = h * 0.1f,
                endY = h * 0.8f + auroraShift2
            ),
            size = size,
            blendMode = BlendMode.Screen
        )

        // AAA Depth Stars
        val random = Random(42)
        for (i in 0 until 40) {
            val starX = random.nextFloat() * w * 0.9f
            val starY = random.nextFloat() * h * 0.9f
            // Some twinkle fast, some slow, vary sizes
            val sizeMod = 0.5f + random.nextFloat() * 2f
            val phase = time / (1000f + random.nextFloat() * 1000f) + random.nextFloat() * 10f
            val twinkle = (sin(phase) + 1f) / 2f
            
            drawCircle(
                color = StarWhite.copy(alpha = twinkle * 0.9f),
                radius = sizeMod,
                center = Offset(starX, starY)
            )
            // Bloom for larger stars
            if (sizeMod > 1.5f) {
                drawCircle(
                    color = StarWhite.copy(alpha = twinkle * 0.3f),
                    radius = sizeMod * 3f,
                    center = Offset(starX, starY)
                )
            }
        }

        // Shooting stars (reset when off screen)
        shootingStars.forEach { s ->
            val sx = (s.x - (time / 10f) * s.speed * 3f)
            val sy = (s.y + (time / 10f) * s.speed * 3f)
            
            // Draw only if it's recently visible to avoid constant streams
            val localTimeMod = (time + s.size * 100000) % 15000
            if (localTimeMod < 1500) {
                val cycleOffset = localTimeMod / 1500f
                val currentX = s.x - cycleOffset * w * 1.5f
                val currentY = s.y + cycleOffset * h * 1.5f
                
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        start = Offset(currentX, currentY),
                        end = Offset(currentX + 150f, currentY - 150f)
                    ),
                    start = Offset(currentX, currentY),
                    end = Offset(currentX + 150f, currentY - 150f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Deep Moon Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(MoonCream.copy(alpha = 0.5f), Color.Transparent),
                center = center,
                radius = radius * 2.5f
            ),
            radius = radius * 2.5f,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(MoonShadow.copy(alpha = 0.25f), Color.Transparent),
                center = center,
                radius = radius * 4f
            ),
            radius = radius * 4f,
            center = center
        )

        // Moon Body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, MoonCream, MoonShadow.copy(alpha = 0.4f)),
                center = center,
                radius = radius
            ),
            radius = radius * moonPulse,
            center = center
        )

        // Detailed Craters
        drawCircle(MoonShadow.copy(alpha = 0.25f), radius * 0.22f, center + Offset(-radius * 0.25f, -radius * 0.15f))
        drawCircle(MoonShadow.copy(alpha = 0.15f), radius * 0.12f, center + Offset(-radius * 0.4f, -radius * 0.05f))
        drawCircle(MoonShadow.copy(alpha = 0.2f), radius * 0.18f, center + Offset(radius * 0.2f, radius * 0.35f))
        drawCircle(MoonShadow.copy(alpha = 0.1f), radius * 0.08f, center + Offset(radius * 0.4f, radius * 0.1f))
        drawCircle(MoonShadow.copy(alpha = 0.12f), radius * 0.1f, center + Offset(-radius * 0.1f, radius * 0.4f))

        // Crescent Mask
        drawCircle(
            color = Color(0xFF1A237E), // Dark sky color matching Hero card gradient
            radius = radius * 0.95f,
            center = center + Offset(radius * 0.35f, -radius * 0.25f),
            blendMode = BlendMode.SrcOver
        )
    }
}

@Composable
fun PartlyCloudyBackground(modifier: Modifier = Modifier, isDay: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "partly_cloudy")
    val drift by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(6000, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)

        // Draw Sun or Moon first
        if (isDay) {
            val sunCenter = center + Offset(-w * 0.2f, -h * 0.15f)
            val sunRadius = h * 0.12f
            drawCircle(SunYellow, sunRadius, sunCenter)
            drawCircle(
                Brush.radialGradient(listOf(SunYellow.copy(alpha = 0.4f), Color.Transparent), sunCenter, sunRadius * 2f),
                sunRadius * 2f, sunCenter
            )
        } else {
            val moonCenter = center + Offset(-w * 0.2f, -h * 0.15f)
            val moonRadius = h * 0.1f
            drawCircle(MoonCream, moonRadius, moonCenter)
        }

        // Clouds with drift - Triple Layer Depth
        // 1. Distant small cloud
        drawCloud(
            center = center + Offset(-w * 0.25f - drift * 0.5f, -h * 0.05f),
            size = h * 0.22f,
            color = CloudShadow,
            alpha = 0.5f
        )
        // 2. Main foreground cloud
        drawCloud(
            center = center + Offset(drift, h * 0.05f),
            size = h * 0.42f,
            color = CloudWhite,
            alpha = 0.95f
        )
        // 3. Side small cloud
        drawCloud(
            center = center + Offset(w * 0.25f + drift * 0.3f, h * 0.15f),
            size = h * 0.28f,
            color = CloudWhite,
            alpha = 0.8f
        )
    }
}

@Composable
fun OvercastBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "overcast_evolved")
    val drift by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(tween(25000, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)

        // Triple-layered dense overcast system
        // Layer 1: Deep Distant Storm Clouds
        drawCloud(center + Offset(-w * 0.15f + (drift * 0.2f), -h * 0.12f), h * 0.55f, CloudStorm, 0.45f)
        drawCloud(center + Offset(w * 0.2f - (drift * 0.3f), -h * 0.08f), h * 0.48f, CloudStorm, 0.35f)
        
        // Layer 2: Main Dense Mid-layer
        drawCloud(center + Offset(drift * 0.5f, -h * 0.02f), h * 0.65f, CloudShadow, 0.75f)
        drawCloud(center + Offset(-w * 0.2f - (drift * 0.4f), h * 0.08f), h * 0.58f, CloudShadow, 0.65f)
        
        // Layer 3: Foreground High-detail Overcast
        drawCloud(center + Offset(drift * 0.9f, h * 0.18f), h * 0.72f, CloudWhite, 0.98f)
        drawCloud(center + Offset(-w * 0.3f - (drift * 0.7f), h * 0.28f), h * 0.52f, CloudWhite, 0.88f)
    }
}

@Composable
fun RainBackground(modifier: Modifier = Modifier, heavy: Boolean = false) {
    val time by rememberTime()
    val particleCount = if (heavy) 120 else 50
    val particles = rememberParticles(particleCount, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)
        val cloudSize = h * 0.45f
        val cloudCenter = center + Offset(0f, h * 0.05f)

        // Triple-layered Storm Cloud
        drawCloud(cloudCenter + Offset(-w * 0.1f, -h * 0.1f), cloudSize * 0.8f, CloudShadow, 0.6f)
        drawCloud(cloudCenter, cloudSize, if (heavy) CloudStorm else CloudShadow, 0.95f)

        // AAA Rain particles with Parallax and Splashes
        val spawnWidth = cloudSize * 1.5f
        val spawnStartY = cloudCenter.y + h * 0.05f
        val spawnHeight = h - spawnStartY

        particles.forEach { p ->
            val drift = (time / 10f) * 2f * p.z
            val x = (p.x + drift) % spawnWidth + (cloudCenter.x - spawnWidth / 2)
            val y = (p.y + (time / 10f) * p.speed) % spawnHeight + spawnStartY
            
            // Draw Rain Drop
            drawLine(
                color = RainBlue.copy(alpha = p.alpha),
                start = Offset(x, y),
                end = Offset(x - 2f * p.z, y + p.speed * 2f * p.z),
                strokeWidth = p.size * p.z,
                cap = StrokeCap.Round
            )
            
            // AAA Water Splashes at bottom
            val bottomLimit = spawnStartY + spawnHeight - 20f
            if (y > bottomLimit) {
                val splashProgress = (y - bottomLimit) / 20f
                val splashW = p.size * 4f * splashProgress
                val splashH = p.size * 1.5f * splashProgress
                drawOval(
                    color = Color.White.copy(alpha = p.alpha * (1f - splashProgress)),
                    topLeft = Offset(x - splashW / 2, bottomLimit - splashH / 2),
                    size = Size(splashW, splashH)
                )
            }
        }
    }
}

@Composable
fun DrizzleBackground(modifier: Modifier = Modifier) {
    val time by rememberTime()
    val particles = rememberParticles(15, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)
        val cloudSize = h * 0.45f
        val cloudCenter = center + Offset(0f, h * 0.05f)

        // Soft overcast clouds
        drawCloud(cloudCenter + Offset(-w * 0.15f, -h * 0.05f), cloudSize * 0.7f, CloudShadow, 0.4f)
        drawCloud(cloudCenter, cloudSize, CloudShadow, 0.8f)

        // AAA Ultra-light Mist Drizzle
        val spawnWidth = cloudSize * 1.4f
        val spawnStartY = cloudCenter.y + h * 0.1f
        val spawnHeight = h - spawnStartY

        particles.forEach { p ->
            val drift = (time / 15f) * p.z
            val x = (p.x + drift) % spawnWidth + (cloudCenter.x - spawnWidth / 2)
            val y = (p.y + (time / 15f) * p.speed * 0.4f) % spawnHeight + spawnStartY
            
            // Mist particles instead of harsh lines
            drawCircle(
                color = RainBlue.copy(alpha = p.alpha * 0.6f),
                radius = p.size * 0.8f * p.z,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun SnowBackground(modifier: Modifier = Modifier) {
    val time by rememberTime()
    val particles = rememberParticles(100, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)
        val cloudSize = h * 0.35f
        val cloudCenter = center + Offset(0f, -h * 0.1f)

        // Layered Winter Clouds
        drawCloud(cloudCenter + Offset(w * 0.1f, -h * 0.05f), cloudSize * 0.8f, CloudShadow, 0.5f)
        drawCloud(cloudCenter, cloudSize, CloudWhite, 0.95f)

        val spawnWidth = cloudSize * 1.5f
        val spawnStartY = cloudCenter.y + h * 0.05f
        val spawnHeight = h - spawnStartY

        // AAA Blizzard Particles
        particles.forEach { p ->
            // Complex swirling wind
            val baseDrift = sin((time + p.x) / (500f * p.z)) * 30f * p.z
            val gust = sin((time + p.y) / 1500f) * 50f * p.z
            val fallSpeed = (time / 20f) * p.speed * 0.5f
            
            val x = (p.x + baseDrift + gust) % spawnWidth + (cloudCenter.x - spawnWidth / 2)
            val y = (p.y + fallSpeed) % spawnHeight + spawnStartY
            
            // Ultra AAA Depth Bokeh for Snowflakes
            val isForeground = p.z > 1.2f
            val isBackground = p.z < 0.6f
            val flakeAlpha = if (isForeground) p.alpha * 0.8f else if (isBackground) p.alpha * 0.3f else p.alpha
            val flakeRadius = if (isForeground) p.size * 3.5f * p.z else p.size * 1.5f * p.z
            
            val pulse = (sin((time + p.x) / 300f) + 1f) / 2f
            drawCircle(
                color = SnowWhite.copy(alpha = flakeAlpha * (0.6f + 0.4f * pulse)),
                radius = flakeRadius,
                center = Offset(x, y)
            )
            if (isForeground) {
                // Bloom for foreground flakes
                drawCircle(
                    color = SnowWhite.copy(alpha = flakeAlpha * 0.2f),
                    radius = flakeRadius * 2f,
                    center = Offset(x, y),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

@Composable
fun ThunderstormBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "thunder")
    val boltAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5000
                0f at 0
                0f at 4000
                1f at 4050
                0f at 4100
                1f at 4150
                0f at 4300
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bolt"
    )

    val time by rememberTime()
    val particles = rememberParticles(30, Size(1000f, 1000f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)
        val cloudSize = h * 0.45f
        val cloudCenter = center + Offset(-w * 0.05f, -h * 0.1f)

        // Layered dark storm clouds
        drawCloud(cloudCenter + Offset(w * 0.12f, h * 0.05f), cloudSize * 0.85f, CloudShadow, 0.7f)
        drawCloud(cloudCenter, cloudSize, CloudStorm, 1f)
        
        // Rain particles constrained to cloud width
        val spawnWidth = cloudSize * 1.3f
        val spawnStartY = cloudCenter.y + h * 0.05f
        val spawnHeight = h - spawnStartY

        particles.forEach { p ->
            val x = (p.x + (time / 10f) * 2f) % spawnWidth + (cloudCenter.x - spawnWidth / 2)
            val y = (p.y + (time / 10f) * p.speed) % spawnHeight + spawnStartY
            
            drawLine(
                color = RainBlue.copy(alpha = p.alpha),
                start = Offset(x, y),
                end = Offset(x - 2f * p.z, y + p.speed * 2f * p.z),
                strokeWidth = p.size * p.z,
                cap = StrokeCap.Round
            )
        }

        // AAA Multi-branch Lightning Strike
        if (boltAlpha > 0f) {
            val start = cloudCenter + Offset(0f, h * 0.05f)
            val path = Path().apply {
                // Main Trunk
                moveTo(start.x, start.y)
                lineTo(start.x - w * 0.05f, start.y + h * 0.15f)
                lineTo(start.x + w * 0.03f, start.y + h * 0.25f)
                lineTo(start.x - w * 0.08f, start.y + h * 0.45f)
                
                // Branch 1 (Left)
                moveTo(start.x - w * 0.05f, start.y + h * 0.15f)
                lineTo(start.x - w * 0.15f, start.y + h * 0.22f)
                lineTo(start.x - w * 0.12f, start.y + h * 0.32f)

                // Branch 2 (Right)
                moveTo(start.x + w * 0.03f, start.y + h * 0.25f)
                lineTo(start.x + w * 0.12f, start.y + h * 0.35f)
                lineTo(start.x + w * 0.08f, start.y + h * 0.4f)
            }
            
            // Outer intense bloom
            drawPath(
                path = path,
                color = LightningYellow.copy(alpha = boltAlpha * 0.5f),
                style = Stroke(width = 25f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Secondary glow
            drawPath(
                path = path,
                color = LightningYellow.copy(alpha = boltAlpha * 0.8f),
                style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Hot white core
            drawPath(
                path = path,
                color = Color.White.copy(alpha = boltAlpha),
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        
        // AAA High-contrast Screen Flash Blending
        if (boltAlpha > 0f) {
            drawRect(
                color = Color.White.copy(alpha = boltAlpha * 0.4f),
                size = size,
                blendMode = BlendMode.Screen
            )
            drawRect(
                color = LightningYellow.copy(alpha = boltAlpha * 0.25f),
                size = size,
                blendMode = BlendMode.Lighten
            )
        }
    }
}

@Composable
fun FogBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "fog_master")
    val time by rememberTime()

    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val center = Offset(w / 2, h / 2)

        // Massive soft cloud core base
        drawCloud(center + Offset(0f, h * 0.1f), h * 0.8f, FogGray, 0.25f)
        drawCloud(center + Offset(-w * 0.2f, h * 0.2f), h * 0.6f, FogGray, 0.2f)
        val layers = 5
        for (i in 0 until layers) {
            val speedMultipier = 1f + (i * 0.5f)
            val drift = (time / 40f * speedMultipier) % (w * 1.5f)
            val yPos = h * (0.55f + (i * 0.08f))
            val layerAlpha = 0.2f + (i * 0.1f)
            val thickness = h * (0.15f + (i * 0.05f))

            // Draw a wide horizontal mist band
            withTransform({
                translate(drift - w * 0.75f, yPos)
            }) {
                val fogBrush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.3f to FogGray.copy(alpha = layerAlpha),
                    0.7f to FogGray.copy(alpha = layerAlpha),
                    1.0f to Color.Transparent,
                    startX = 0f,
                    endX = w * 1.5f
                )
                
                // Draw a wavy path for the fog top
                val fogPath = Path().apply {
                    moveTo(0f, thickness)
                    var x = 0f
                    while (x < w * 1.5f) {
                        val wave = sin((time / 1000f) + (x / 100f) + i) * 15f
                        lineTo(x, wave)
                        x += 50f
                    }
                    lineTo(w * 1.5f, thickness)
                    close()
                }
                
                drawPath(fogPath, fogBrush)
            }

            // Occasionally add a "cloud puff" within the fog for texture
            val puffX = (drift * 1.2f + (i * 200f)) % w
            drawCloud(
                center = Offset(puffX, yPos + thickness / 2),
                size = thickness * 2,
                color = FogGray,
                alpha = layerAlpha * 0.5f
            )
        }
    }
}

private val SineEaseInOut = Easing { x ->
    -0.5f * (cos(PI.toFloat() * x) - 1f)
}
