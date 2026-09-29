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
            trailColor = Color(0xFFFEF08A),
            ability = StrikerAbility.BALANCED
        ),
        StrikerConfig(
            id = "ruby_emperor",
            name = "Ruby Emperor",
            description = "Forged from polished blood ruby, with a fiery crimson trail.",
            price = 600,
            isUnlocked = false,
            primaryColor = Color(0xFFEF4444),
            secondaryColor = Color(0xFF7F1D1D),
            glowColor = Color(0xFFF87171),
            powerMultiplier = 1.15f,
            aimGuideLength = 1.05f,
            weight = 3.2f,
            trailColor = Color(0xFFEF4444),
            ability = StrikerAbility.FIRE_SHOT
        ),
        StrikerConfig(
            id = "emerald_dragon",
            name = "Emerald Dragon",
            description = "Carved jade with serpentine precision: it sees the rebound coming.",
            price = 1500,
            isUnlocked = false,
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF064E3B),
            glowColor = Color(0xFF34D399),
            powerMultiplier = 1.05f,
            aimGuideLength = 1.25f,
            weight = 2.9f,
            trailColor = Color(0xFF10B981),
            ability = StrikerAbility.DRAGON_SIGHT
        ),
        StrikerConfig(
            id = "solar_gold",
            name = "Solar Gold Sunburst",
            description = "Solid gilded tournament striker. Heavy, and it hits like it.",
            price = 3000,
            isUnlocked = false,
            primaryColor = Color(0xFFFBBF24),
            secondaryColor = Color(0xFF78350F),
            glowColor = Color(0xFFFDE047),
            powerMultiplier = 1.25f,
            aimGuideLength = 1.15f,
            weight = 3.4f,
            trailColor = Color(0xFFF59E0B),
            ability = StrikerAbility.HEAVYWEIGHT
        ),
        StrikerConfig(
            id = "cyber_neon",
            name = "Cyberpunk Synth",
            description = "High-tech composite with a frictionless coating and neon pulse.",
            price = 5500,
            isUnlocked = false,
            primaryColor = Color(0xFF06B6D4),
            secondaryColor = Color(0xFF4F46E5),
            glowColor = Color(0xFF22D3EE),
            powerMultiplier = 1.2f,
            aimGuideLength = 1.3f,
            weight = 3.0f,
            trailColor = Color(0xFF06B6D4),
            ability = StrikerAbility.FRICTIONLESS
        ),
        StrikerConfig(
            id = "rose_gold_velvet",
            name = "Rose Diamond Luxury",
            description = "Grandmaster jewel encrusted with diamond flakes. Maximum precision.",
            price = 10000,
            isUnlocked = false,
            primaryColor = Color(0xFFF43F5E),
            secondaryColor = Color(0xFF881337),
            glowColor = Color(0xFFFDA4AF),
            powerMultiplier = 1.3f,
            aimGuideLength = 1.35f,
            weight = 3.3f,
            trailColor = Color(0xFFFB7185),
            ability = StrikerAbility.PRECISION
        )
    )

    val BOARDS = listOf(
        BoardTheme(
            id = "classic_teak",
            name = "Classic Teakwood",
            description = "Standard polished hardwood board with traditional Indian carrom geometric mandala.",
            price = 0,
            isUnlocked = true,
            woodColor = Color(0xFF3E1F0F),
            woodInner = Color(0xFF7A4424),
            feltColor = Color(0xFFE4C690),
            feltPatternColor = Color(0xFF24160E),
            centerCircleColor = Color(0xFFB01E28),
            pocketRimColor = Color(0xFFC9A227),
            accentGold = Color(0xFFD4AF37),
            power = BoardPower.TOURNAMENT
        ),
        BoardTheme(
            id = "royal_indigo",
            name = "Royal Sapphire Velvet",
            description = "Midnight blue velvet with celestial gold markings. A slow, controlled table.",
            price = 900,
            isUnlocked = false,
            woodColor = Color(0xFF151B2C),
            woodInner = Color(0xFF33415F),
            feltColor = Color(0xFF23406B),
            feltPatternColor = Color(0xFFD9C489),
            centerCircleColor = Color(0xFFB8323A),
            pocketRimColor = Color(0xFFD4AF37),
            accentGold = Color(0xFFD4AF37),
            power = BoardPower.VELVET_TOUCH
        ),
        BoardTheme(
            id = "emerald_sanctuary",
            name = "Emerald Casino Felt",
            description = "Lush green casino felt with generous, forgiving pockets.",
            price = 1800,
            isUnlocked = false,
            woodColor = Color(0xFF23180F),
            woodInner = Color(0xFF4E3320),
            feltColor = Color(0xFF1D5C40),
            feltPatternColor = Color(0xFFE9DCB0),
            centerCircleColor = Color(0xFFB8323A),
            pocketRimColor = Color(0xFFD4AF37),
            accentGold = Color(0xFFE9DCB0),
            power = BoardPower.WIDE_POCKETS
        ),
        BoardTheme(
            id = "vintage_mahogany",
            name = "Antique Mahogany",
            description = "Rich dark mahogany with springy hand-set cushions.",
            price = 3500,
            isUnlocked = false,
            woodColor = Color(0xFF2A0E07),
            woodInner = Color(0xFF62220F),
            feltColor = Color(0xFFD9AE74),
            feltPatternColor = Color(0xFF2A140A),
            centerCircleColor = Color(0xFF8E1B1B),
            pocketRimColor = Color(0xFFD4AF37),
            accentGold = Color(0xFFF2DC8F),
            power = BoardPower.LIVELY_CUSHIONS
        ),
        BoardTheme(
            id = "cyber_matrix",
            name = "Cyberpunk Neon Arena",
            description = "Polished carbon composite. The fastest surface in the game.",
            price = 7000,
            isUnlocked = false,
            woodColor = Color(0xFF050811),
            woodInner = Color(0xFF0B1329),
            feltColor = Color(0xFF0C162D),
            feltPatternColor = Color(0xFF00F0FF),
            centerCircleColor = Color(0xFFFF0055),
            pocketRimColor = Color(0xFF00F0FF),
            accentGold = Color(0xFF00F0FF),
            power = BoardPower.HYPER_GLIDE
        )
    )

    /** Carrom men ("gotis"). Each set looks different and has its own power. */
    val COIN_SETS = listOf(
        CoinSet(
            id = "heritage_boxwood",
            name = "Heritage Boxwood",
            description = "Turned boxwood and ebony, lacquered crimson queen.",
            price = 0,
            isUnlocked = true,
            power = CoinPower.BALANCED,
            whiteFace = Color(0xFFE9D9B6), whiteEdge = Color(0xFFA88A5E),
            blackFace = Color(0xFF2B1F19), blackEdge = Color(0xFF0E0907),
            queenFace = Color(0xFFB8232C), queenEdge = Color(0xFF63101A)
        ),
        CoinSet(
            id = "marble_royale",
            name = "Marble Royale",
            description = "Carrara and verde marble, cut heavy.",
            price = 800,
            power = CoinPower.ANCHOR,
            whiteFace = Color(0xFFF1EEE6), whiteEdge = Color(0xFF9C978C),
            blackFace = Color(0xFF203330), blackEdge = Color(0xFF0A1311),
            queenFace = Color(0xFF9E1B32), queenEdge = Color(0xFF4F0A17)
        ),
        CoinSet(
            id = "crystal_glide",
            name = "Crystal Glide",
            description = "Frosted glass discs with a sapphire set.",
            price = 1600,
            power = CoinPower.GLIDE,
            whiteFace = Color(0xFFDDF2FF), whiteEdge = Color(0xFF7EB2D2),
            blackFace = Color(0xFF1C3456), blackEdge = Color(0xFF0A172B),
            queenFace = Color(0xFFE25C8A), queenEdge = Color(0xFF7D1E43)
        ),
        CoinSet(
            id = "magnet_iron",
            name = "Magnet Iron",
            description = "Brushed steel and gunmetal with a copper queen.",
            price = 2800,
            power = CoinPower.MAGNET,
            whiteFace = Color(0xFFD6DADF), whiteEdge = Color(0xFF6D747D),
            blackFace = Color(0xFF30343A), blackEdge = Color(0xFF111316),
            queenFace = Color(0xFFC0602F), queenEdge = Color(0xFF5E2A10)
        ),
        CoinSet(
            id = "jumbo_carnival",
            name = "Jumbo Carnival",
            description = "Big, bright festival discs you can't miss.",
            price = 3600,
            power = CoinPower.JUMBO,
            whiteFace = Color(0xFFFFF0C4), whiteEdge = Color(0xFFD29B3C),
            blackFace = Color(0xFF3B2A6B), blackEdge = Color(0xFF1A1033),
            queenFace = Color(0xFFE8412F), queenEdge = Color(0xFF7A170E)
        ),
        CoinSet(
            id = "golden_mint",
            name = "Golden Mint",
            description = "Struck like coins of the realm. Winning pays better.",
            price = 6000,
            power = CoinPower.MIDAS,
            whiteFace = Color(0xFFF6DE8D), whiteEdge = Color(0xFFA27A22),
            blackFace = Color(0xFF6B4A1A), blackEdge = Color(0xFF2E1E07),
            queenFace = Color(0xFFD0342C), queenEdge = Color(0xFF6A1410)
        )
    )

    /** Dice for Dice Carrom. */
    val DICE = listOf(
        DiceSkin(
            id = "ivory_die",
            name = "Ivory Die",
            description = "Classic bone-white die with ebony pips.",
            price = 0,
            isUnlocked = true,
            power = DicePower.FAIR,
            body = Color(0xFFF5ECD8), edge = Color(0xFFB9A57E), pip = Color(0xFF1B1108)
        ),
        DiceSkin(
            id = "clover_die",
            name = "Lucky Clover",
            description = "Jade green with golden pips.",
            price = 700,
            power = DicePower.LUCKY_REROLL,
            body = Color(0xFF2F7D4F), edge = Color(0xFF15402A), pip = Color(0xFFF2DC8F)
        ),
        DiceSkin(
            id = "crimson_die",
            name = "Royal Crimson",
            description = "Lacquered crimson that refuses to roll blank.",
            price = 2200,
            power = DicePower.NO_BLANKS,
            body = Color(0xFFB8232C), edge = Color(0xFF5E0F16), pip = Color(0xFFF5ECD8)
        ),
        DiceSkin(
            id = "golden_die",
            name = "Golden Die",
            description = "Solid brass. Doubles become triples.",
            price = 4500,
            power = DicePower.GOLDEN_DOUBLE,
            body = Color(0xFFE2BE4E), edge = Color(0xFF8F6C1C), pip = Color(0xFF3A2708)
        )
    )
}
