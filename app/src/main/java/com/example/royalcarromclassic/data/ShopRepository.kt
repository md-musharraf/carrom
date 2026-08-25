package com.example.royalcarromclassic.data

import androidx.compose.ui.graphics.Color

object ShopRepository {
    val STRIKERS = listOf(
        StrikerConfig(
            id = "classic_ivory",
            name = "Classic Ivory",
            description = "Standard tournament ivory striker. Perfectly balanced weight and glide.",
            price = 0,
            isUnlocked = true,
            primaryColor = Color(0xFFFEF3C7),
            secondaryColor = Color(0xFFB45309),
            glowColor = Color(0xFFFBBF24),
            powerMultiplier = 1.0f,
            aimGuideLength = 1.0f,
            weight = 3.0f,
            trailColor = Color(0xFFFEF08A)
        ),
        StrikerConfig(
            id = "ruby_emperor",
            name = "Ruby Emperor",
            description = "Forged from polished blood ruby. Adds extra power and fiery crimson trail.",
            price = 600,
            isUnlocked = false,
            primaryColor = Color(0xFFEF4444),
            secondaryColor = Color(0xFF7F1D1D),
            glowColor = Color(0xFFF87171),
            powerMultiplier = 1.15f,
            aimGuideLength = 1.05f,
            weight = 3.2f,
            trailColor = Color(0xFFEF4444)
        ),
        StrikerConfig(
            id = "emerald_dragon",
            name = "Emerald Dragon",
            description = "Infused with serpentine precision. Grants extended laser trajectory projection.",
            price = 1500,
            isUnlocked = false,
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF064E3B),
            glowColor = Color(0xFF34D399),
            powerMultiplier = 1.05f,
            aimGuideLength = 1.25f,
            weight = 2.9f,
            trailColor = Color(0xFF10B981)
        ),
        StrikerConfig(
            id = "solar_gold",
            name = "Solar Gold Sunburst",
            description = "Solid 24k gold gilded tournament striker. Unmatched kinetic impact force.",
            price = 3000,
            isUnlocked = false,
            primaryColor = Color(0xFFFBBF24),
            secondaryColor = Color(0xFF78350F),
            glowColor = Color(0xFFFDE047),
            powerMultiplier = 1.25f,
            aimGuideLength = 1.15f,
            weight = 3.4f,
            trailColor = Color(0xFFF59E0B)
        ),
        StrikerConfig(
            id = "cyber_neon",
            name = "Cyberpunk Synth",
            description = "High-tech composite with frictionless electromagnetic coating and neon pulse.",
            price = 5500,
            isUnlocked = false,
            primaryColor = Color(0xFF06B6D4),
            secondaryColor = Color(0xFF4F46E5),
            glowColor = Color(0xFF22D3EE),
            powerMultiplier = 1.2f,
            aimGuideLength = 1.3f,
            weight = 3.0f,
            trailColor = Color(0xFF06B6D4)
        ),
        StrikerConfig(
            id = "rose_gold_velvet",
            name = "Rose Diamond Luxury",
            description = "Grandmaster jewel encrusted with diamond flakes. Max precision and maximum style.",
            price = 10000,
            isUnlocked = false,
            primaryColor = Color(0xFFF43F5E),
            secondaryColor = Color(0xFF881337),
            glowColor = Color(0xFFFDA4AF),
            powerMultiplier = 1.3f,
            aimGuideLength = 1.35f,
            weight = 3.3f,
            trailColor = Color(0xFFFB7185)
        )
    )

    val BOARDS = listOf(
        BoardTheme(
            id = "classic_teak",
            name = "Classic Teakwood",
            description = "Standard polished hardwood board with traditional Indian carrom geometric mandala.",
            price = 0,
            isUnlocked = true,
            woodColor = Color(0xFF2A160C),
            woodInner = Color(0xFF3D2012),
            feltColor = Color(0xFFD6A873),
            feltPatternColor = Color(0xFF8B5A2B),
            centerCircleColor = Color(0xFFB91C1C),
            pocketRimColor = Color(0xFFD97706),
            accentGold = Color(0xFFFBBF24)
        ),
        BoardTheme(
            id = "royal_indigo",
            name = "Royal Sapphire Velvet",
            description = "Midnight blue plush velvet board with bright celestial gold markings.",
            price = 900,
            isUnlocked = false,
            woodColor = Color(0xFF0F172A),
            woodInner = Color(0xFF1E293B),
            feltColor = Color(0xFF1E3A5F),
            feltPatternColor = Color(0xFF60A5FA),
            centerCircleColor = Color(0xFFEF4444),
            pocketRimColor = Color(0xFFFBBF24),
            accentGold = Color(0xFF60A5FA)
        ),
        BoardTheme(
            id = "emerald_sanctuary",
            name = "Emerald Casino Felt",
            description = "Lush green tournament felt inspired by international carrom championships.",
            price = 1800,
            isUnlocked = false,
            woodColor = Color(0xFF1C1917),
            woodInner = Color(0xFF292524),
            feltColor = Color(0xFF155E42),
            feltPatternColor = Color(0xFF34D399),
            centerCircleColor = Color(0xFFDC2626),
            pocketRimColor = Color(0xFFF59E0B),
            accentGold = Color(0xFF34D399)
        ),
        BoardTheme(
            id = "vintage_mahogany",
            name = "Antique Mahogany",
            description = "Rich dark mahogany with hand-carved mother-of-pearl borders and warm amber felt.",
            price = 3500,
            isUnlocked = false,
            woodColor = Color(0xFF200C06),
            woodInner = Color(0xFF3B160C),
            feltColor = Color(0xFFC8955A),
            feltPatternColor = Color(0xFF5C2D13),
            centerCircleColor = Color(0xFF991B1B),
            pocketRimColor = Color(0xFFEAB308),
            accentGold = Color(0xFFFDE047)
        ),
        BoardTheme(
            id = "cyber_matrix",
            name = "Cyberpunk Neon Arena",
            description = "Futuristic carbon composite playing surface with reactive neon circuitry.",
            price = 7000,
            isUnlocked = false,
            woodColor = Color(0xFF050811),
            woodInner = Color(0xFF0B1329),
            feltColor = Color(0xFF0C162D),
            feltPatternColor = Color(0xFF00F0FF),
            centerCircleColor = Color(0xFFFF0055),
            pocketRimColor = Color(0xFF00F0FF),
            accentGold = Color(0xFF00F0FF)
        )
    )
}
