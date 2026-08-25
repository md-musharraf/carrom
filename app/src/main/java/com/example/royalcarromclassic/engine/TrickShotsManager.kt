package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.TrickShotLevel
import com.example.royalcarromclassic.data.Vector2D

object TrickShotsManager {
    private const val CX = BoardGeometry.BOARD_SIZE / 2f
    private const val CY = BoardGeometry.BOARD_SIZE / 2f

    val LEVELS = listOf(
        TrickShotLevel(
            id = 1,
            title = "The Direct Cut",
            description = "Pocket the White disc into the Bottom-Right pocket with a clean direct cut.",
            targetPockets = listOf(2),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX + 70f, CY + 60f))
            ),
            strikerPos = Vector2D(CX - 60f, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = true,
            hint = "Line up your aim guide with the ghost target aiming directly into the bottom-right pocket."
        ),
        TrickShotLevel(
            id = 2,
            title = "The Classic Bank Shot",
            description = "Rebound the striker off the Right Cushion to pot the White piece into Bottom-Left pocket.",
            targetPockets = listOf(3),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX + 140f, CY - 30f))
            ),
            strikerPos = Vector2D(CX, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Aim high at the right wall so the reflection angles down towards the target piece."
        ),
        TrickShotLevel(
            id = 3,
            title = "Double Pocket Split",
            description = "Pot both White pieces into the two bottom pockets in a single strike!",
            targetPockets = listOf(2, 3),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX - 50f, CY + 50f)),
                Pair(PieceType.WHITE, Vector2D(CX + 50f, CY + 50f))
            ),
            strikerPos = Vector2D(CX, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Aim dead center between the two pieces with maximum power to split them apart."
        ),
        TrickShotLevel(
            id = 4,
            title = "Queen Rescue",
            description = "The Red Queen is blocked by two black guard pieces. Pot the Queen without potting any black pieces!",
            targetPockets = listOf(0),
            pieces = listOf(
                Pair(PieceType.QUEEN, Vector2D(CX, CY - 60f)),
                Pair(PieceType.BLACK, Vector2D(CX - 35f, CY - 20f)),
                Pair(PieceType.BLACK, Vector2D(CX + 35f, CY - 20f))
            ),
            strikerPos = Vector2D(CX - 130f, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Bank the shot off the left cushion to bypass the black guards from the side."
        ),
        TrickShotLevel(
            id = 5,
            title = "The Long Distance Snipe",
            description = "Pocket the White disc in the far Top-Right pocket across the full diagonal.",
            targetPockets = listOf(1),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX + 80f, CY - 100f))
            ),
            strikerPos = Vector2D(CX - 150f, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Use high power and adjust the fine-tune aim ring carefully."
        ),
        TrickShotLevel(
            id = 6,
            title = "Cushion Hugger",
            description = "The disc is resting against the top wall. Angle your striker to roll it along the rail into Top-Left pocket.",
            targetPockets = listOf(0),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX - 100f, 75f))
            ),
            strikerPos = Vector2D(CX + 120f, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Aim slightly behind the piece near the rail to impart forward rail-glide momentum."
        ),
        TrickShotLevel(
            id = 7,
            title = "Queen & Cover Combo",
            description = "Pocket the Queen and the White cover piece in 2 consecutive shots.",
            targetPockets = listOf(0, 1),
            pieces = listOf(
                Pair(PieceType.QUEEN, Vector2D(CX - 60f, CY - 50f)),
                Pair(PieceType.WHITE, Vector2D(CX + 60f, CY - 50f))
            ),
            strikerPos = Vector2D(CX, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 2,
            isUnlocked = false,
            hint = "First pot the Queen into Top-Left, then reposition striker to cover with White into Top-Right."
        ),
        TrickShotLevel(
            id = 8,
            title = "The Two-Wall Bank Rebound",
            description = "Rebound off the Left Cushion AND Top Cushion to pot the center piece into Bottom-Right pocket!",
            targetPockets = listOf(2),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX, CY))
            ),
            strikerPos = Vector2D(CX + 150f, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 1,
            isUnlocked = false,
            hint = "Aim sharply at the upper-left corner of the board with 95% power."
        ),
        TrickShotLevel(
            id = 9,
            title = "The Golden Trio Sweep",
            description = "Clear all 3 White pieces distributed across the board in 2 shots.",
            targetPockets = listOf(0, 1, 2, 3),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX - 120f, CY - 40f)),
                Pair(PieceType.WHITE, Vector2D(CX, CY + 70f)),
                Pair(PieceType.WHITE, Vector2D(CX + 120f, CY - 40f))
            ),
            strikerPos = Vector2D(CX, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 2,
            isUnlocked = false,
            hint = "Use carom collision physics: let the striker ricochet off the bottom piece into the left piece."
        ),
        TrickShotLevel(
            id = 10,
            title = "Grandmaster's Final Trial",
            description = "Complex puzzle: Pot 4 White pieces while avoiding the 4 Black hazard pieces in 3 shots.",
            targetPockets = listOf(0, 1, 2, 3),
            pieces = listOf(
                Pair(PieceType.WHITE, Vector2D(CX - 80f, CY - 80f)),
                Pair(PieceType.WHITE, Vector2D(CX + 80f, CY - 80f)),
                Pair(PieceType.WHITE, Vector2D(CX - 80f, CY + 80f)),
                Pair(PieceType.WHITE, Vector2D(CX + 80f, CY + 80f)),
                Pair(PieceType.BLACK, Vector2D(CX, CY - 100f)),
                Pair(PieceType.BLACK, Vector2D(CX, CY + 100f)),
                Pair(PieceType.BLACK, Vector2D(CX - 100f, CY)),
                Pair(PieceType.BLACK, Vector2D(CX + 100f, CY))
            ),
            strikerPos = Vector2D(CX, BoardGeometry.BASELINE_BOTTOM_Y),
            maxShots = 3,
            isUnlocked = false,
            hint = "Precision cuts are required. Take out bottom pieces first before tackling top pieces."
        )
    )
}
