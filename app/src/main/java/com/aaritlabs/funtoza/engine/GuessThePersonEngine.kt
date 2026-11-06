package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

enum class PersonGender { MALE, FEMALE }
enum class PersonSkinTone { LIGHT, MEDIUM, DARK }
enum class PersonHairLength { BALD, SHORT, LONG }
enum class PersonHairColor { BLONDE, BROWN, BLACK, RED, GREY }
enum class PersonEyeColor { BLUE, BROWN, GREEN }
enum class HatType { NONE, CAP, BEANIE, FEDORA, BERET, BUCKET }

data class PersonCharacter(
    val id: Int,
    val name: String,
    val gender: PersonGender,
    val skinTone: PersonSkinTone,
    val hairColor: PersonHairColor,
    val hairLength: PersonHairLength,
    val eyeColor: PersonEyeColor,
    val hasGlasses: Boolean,
    val hasHat: Boolean,
    val hatType: HatType = HatType.NONE,
    val hasBeard: Boolean = false,
    val hasMustache: Boolean = false,
    val hasEarrings: Boolean = false
)

enum class QuestionCategory(val title: String, val icon: String) {
    GENDER("GENDER", "👫"),
    EYE_COLOR("EYE COLOR", "👁️"),
    HAIR("HAIR", "💇"),
    HAIR_COLOR("HAIR COLOR", "💇‍♀️"),
    SKIN_TONE("SKIN TONE", "🧑🏽"),
    ACCESSORIES("ACCESSORIES", "👓"),
    FACIAL_HAIR("FACIAL HAIR", "👨")
}

data class QuestionOption(
    val category: QuestionCategory,
    val label: String,
    val iconEmoji: String,
    val test: (PersonCharacter) -> Boolean
)

data class CharacterCardState(
    val character: PersonCharacter,
    val rect: RectF = RectF(),
    var isFlippedDown: Boolean = false
)

enum class GamePhase {
    SELECT_P1_SECRET,
    PASS_TO_P2_FOR_SECRET,
    SELECT_P2_SECRET,
    PASS_PHONE_BETWEEN_TURNS,
    TURN_QUESTION_PICK,       // Image 2: 7 Category buttons below board
    TURN_CATEGORY_POPUP,      // Modal choosing specific question in category
    TURN_ELIMINATION,         // Image 3, 4, 5: Question answer card shown, player taps cards to eliminate
    WINNER_REVEAL_POPUP       // Image 1: Side by side guessed vs picked comparison card
}

/**
 * Authentic Guess The Person Engine
 * Reverse-engineered from reference APK module 145_GUESS_THE_PERSON.
 *
 * Exact Flow & Visual Matching:
 * - 30 iconic characters in a 5 columns x 6 rows grid inside a slate blue board container.
 * - Turn Question Pick: 7 clean white category buttons (Image 2).
 * - Question Answer Card: Big white card showing question + green "Yes!" / red "No!" (Image 3 & 4).
 * - Elimination Phase: Tapping cards flips them down to dark blue backs (Image 4).
 * - "PASS" button (when > 1 cards active) vs "GUESS" button (when 1 card left) (Image 4 & 5).
 * - Undo button (↶) reverting card flips.
 * - Winner Reveal: Side-by-side "You guessed" vs "Opponent picked" cards with celebration (Image 1).
 */
class GuessThePersonEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, min(totalPlayers, 2), soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // 30 Characters Roster (5 Cols x 6 Rows) in exact visual layout of reference game
    private val roster = listOf(
        // Row 1
        PersonCharacter(1, "Lucy", PersonGender.FEMALE, PersonSkinTone.MEDIUM, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, true, HatType.FEDORA, hasEarrings = true),
        PersonCharacter(2, "Tom", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.GREY, PersonHairLength.BALD, PersonEyeColor.BROWN, true, false, hasMustache = true),
        PersonCharacter(3, "Sara", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.BLACK, PersonHairLength.LONG, PersonEyeColor.BROWN, true, true, HatType.BEANIE, hasEarrings = true),
        PersonCharacter(4, "Emma", PersonGender.FEMALE, PersonSkinTone.DARK, PersonHairColor.GREY, PersonHairLength.LONG, PersonEyeColor.BROWN, false, false),
        PersonCharacter(5, "Henry", PersonGender.MALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, false, hasBeard = true),

        // Row 2
        PersonCharacter(6, "Rose", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BLUE, false, true, HatType.BEANIE),
        PersonCharacter(7, "Jeff", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BLONDE, PersonHairLength.LONG, PersonEyeColor.BROWN, true, false, hasBeard = true),
        PersonCharacter(8, "Lia", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.GREY, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, false, hasEarrings = true),
        PersonCharacter(9, "Rob", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, true, HatType.CAP, hasBeard = true),
        PersonCharacter(10, "Aria", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.GREY, PersonHairLength.LONG, PersonEyeColor.BROWN, true, false),

        // Row 3
        PersonCharacter(11, "Bob", PersonGender.MALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, false),
        PersonCharacter(12, "Bella", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.RED, PersonHairLength.SHORT, PersonEyeColor.GREEN, false, false),
        PersonCharacter(13, "Sophia", PersonGender.FEMALE, PersonSkinTone.MEDIUM, PersonHairColor.BLONDE, PersonHairLength.LONG, PersonEyeColor.BROWN, false, false),
        PersonCharacter(14, "Jack", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.GREY, PersonHairLength.SHORT, PersonEyeColor.GREEN, false, true, HatType.BEANIE, hasBeard = true),
        PersonCharacter(15, "Jose", PersonGender.MALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.BALD, PersonEyeColor.BROWN, false, false, hasBeard = true),

        // Row 4
        PersonCharacter(16, "Theo", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BLONDE, PersonHairLength.SHORT, PersonEyeColor.BLUE, false, false),
        PersonCharacter(17, "Annie", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.RED, PersonHairLength.LONG, PersonEyeColor.BLUE, false, false),
        PersonCharacter(18, "Paul", PersonGender.MALE, PersonSkinTone.MEDIUM, PersonHairColor.BROWN, PersonHairLength.LONG, PersonEyeColor.GREEN, false, false, hasBeard = true),
        PersonCharacter(19, "Olivia", PersonGender.FEMALE, PersonSkinTone.DARK, PersonHairColor.BLONDE, PersonHairLength.LONG, PersonEyeColor.GREEN, false, false, hasEarrings = true),
        PersonCharacter(20, "Chloe", PersonGender.FEMALE, PersonSkinTone.MEDIUM, PersonHairColor.BROWN, PersonHairLength.LONG, PersonEyeColor.GREEN, false, false, hasEarrings = true),

        // Row 5
        PersonCharacter(21, "Nora", PersonGender.FEMALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, true, true, HatType.BERET),
        PersonCharacter(22, "James", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BROWN, PersonHairLength.SHORT, PersonEyeColor.BROWN, true, true, HatType.CAP),
        PersonCharacter(23, "Bill", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BLONDE, PersonHairLength.SHORT, PersonEyeColor.BLUE, false, false),
        PersonCharacter(24, "Julia", PersonGender.FEMALE, PersonSkinTone.DARK, PersonHairColor.GREY, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, false, hasEarrings = true),
        PersonCharacter(25, "Naomi", PersonGender.FEMALE, PersonSkinTone.LIGHT, PersonHairColor.BLACK, PersonHairLength.LONG, PersonEyeColor.BROWN, false, false, hasEarrings = true),

        // Row 6
        PersonCharacter(26, "Ryan", PersonGender.MALE, PersonSkinTone.LIGHT, PersonHairColor.BROWN, PersonHairLength.SHORT, PersonEyeColor.BLUE, true, true, HatType.CAP, hasMustache = true),
        PersonCharacter(27, "David", PersonGender.MALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.SHORT, PersonEyeColor.BROWN, false, false, hasMustache = true),
        PersonCharacter(28, "Mila", PersonGender.FEMALE, PersonSkinTone.MEDIUM, PersonHairColor.BLONDE, PersonHairLength.LONG, PersonEyeColor.BROWN, true, false),
        PersonCharacter(29, "John", PersonGender.MALE, PersonSkinTone.MEDIUM, PersonHairColor.GREY, PersonHairLength.SHORT, PersonEyeColor.BLUE, false, true, HatType.BUCKET, hasBeard = true),
        PersonCharacter(30, "Ben", PersonGender.MALE, PersonSkinTone.DARK, PersonHairColor.BLACK, PersonHairLength.BALD, PersonEyeColor.BROWN, true, false)
    )

    // Cached authentic character face bitmaps
    private val characterBitmaps = mutableMapOf<Int, Bitmap>()

    // Separate boards for Player 1 and Player 2
    private val p1Cards = roster.map { CharacterCardState(it) }
    private val p2Cards = roster.map { CharacterCardState(it) }

    // Secret chosen characters
    private var p1SecretPerson: PersonCharacter? = null
    private var p2SecretPerson: PersonCharacter? = null

    // Selected card during secret pick phase
    private var p1SelectedSecretIndex: Int = -1
    private var p2SelectedSecretIndex: Int = -1

    // Game state
    private var currentPhase = GamePhase.SELECT_P1_SECRET
    private var activeTurnPlayerId = PlayerId.PLAYER_1
    private var isRoundEnding = false
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null
    private var questionWarningTimer = 0f

    // Question & Answer state
    private var currentQuestionAsked: QuestionOption? = null
    private var currentQuestionAnswer: Boolean = false
    private var selectedCategory: QuestionCategory? = null

    // Undo tracking stack for the current turn
    private val currentTurnFlippedCards = mutableListOf<CharacterCardState>()

    // Bot AI state
    private var botThinkingTimer = -1f
    private var botIsActing = false
    private val botAskedQuestions = mutableSetOf<String>()

    // Winner popup state
    private var winnerGuessedPerson: PersonCharacter? = null
    private var winnerActualPerson: PersonCharacter? = null
    private var winnerDisplayName: String = ""

    // Layout Rectangles
    private val boardContainerRect = RectF()
    private val answerCardRect = RectF()
    private val btnPassOrGuessRect = RectF()
    private val btnUndoRect = RectF()
    private val btnConfirmSecretRect = RectF()

    // Pass phone screen elements
    private val passPhoneCardRect = RectF()
    private val passPhoneOkBtnRect = RectF()

    // Category Buttons (Image 2)
    private val categoryButtonRects = mutableMapOf<QuestionCategory, RectF>()

    // Sub-question dialog geometry
    private val subQuestionDialogRect = RectF()
    private val subQuestionCloseBtnRect = RectF()
    private val subQuestionButtonRects = mutableListOf<Pair<QuestionOption, RectF>>()

    // Winner popup geometry (Image 1)
    private val winnerCardRect = RectF()
    private val winnerGuessedBoxRect = RectF()
    private val winnerPickedBoxRect = RectF()
    private val winnerBannerRect = RectF()

    // Drawing tools
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    init {
        if (totalPlayers == 1 && players.size == 1) {
            players.add(Player(id = PlayerId.PLAYER_2, isBot = true))
        }
    }

    override fun calculateControlZones() {
        controlZones.clear() // Custom canvas touch handling
    }

    override fun initGame() {
        loadCharacterBitmaps()
        p1Cards.forEach { it.isFlippedDown = false }
        p2Cards.forEach { it.isFlippedDown = false }
        p1SecretPerson = null
        p2SecretPerson = null
        p1SelectedSecretIndex = -1
        p2SelectedSecretIndex = -1
        currentPhase = GamePhase.SELECT_P1_SECRET
        activeTurnPlayerId = PlayerId.PLAYER_1
        currentQuestionAsked = null
        selectedCategory = null
        currentTurnFlippedCards.clear()
        botAskedQuestions.clear()
        botThinkingTimer = -1f
        botIsActing = false
        isRoundEnding = false
        roundEndDelayTimer = -1f
        pendingWinner = null
        questionWarningTimer = 0f
        winnerGuessedPerson = null
        winnerActualPerson = null
    }

    private fun loadCharacterBitmaps() {
        if (characterBitmaps.isNotEmpty()) return
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        roster.forEach { c ->
            val resName = "char_${c.name.lowercase()}"
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                BitmapFactory.decodeResource(context.resources, resId, opts)?.let {
                    characterBitmaps[c.id] = it
                }
            }
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        loadCharacterBitmaps()

        val isPortrait = h > w
        // 84dp top safe area guarantees no overlap with Pause button and Title bar
        val topSafeArea = if (isPortrait) 84f * density else 68f * density
        val bottomMargin = 12f * density

        val containerMarginH = 14f * density
        val containerW = w - containerMarginH * 2

        // Grid calculation: 5 columns x 6 rows
        val cols = 5
        val rows = 6
        val cardSpacing = 4f * density
        val containerPadding = 6f * density

        val gridInnerW = containerW - containerPadding * 2
        val cardW = (gridInnerW - cardSpacing * (cols - 1)) / cols
        // Aspect ratio 1.0 : 1.25
        val cardH = cardW * 1.25f

        val gridInnerH = rows * cardH + (rows - 1) * cardSpacing
        val containerH = gridInnerH + containerPadding * 2

        boardContainerRect.set(containerMarginH, topSafeArea, containerMarginH + containerW, topSafeArea + containerH)

        // Assign positions for each card
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx < roster.size) {
                    val l = boardContainerRect.left + containerPadding + c * (cardW + cardSpacing)
                    val t = boardContainerRect.top + containerPadding + r * (cardH + cardSpacing)
                    val rect = RectF(l, t, l + cardW, t + cardH)
                    p1Cards[idx].rect.set(rect)
                    p2Cards[idx].rect.set(rect)
                }
            }
        }

        val lowerAreaTop = boardContainerRect.bottom + 8f * density
        val lowerAreaHeight = h - lowerAreaTop - bottomMargin

        // 1. Category Buttons Layout (Image 2)
        // Row 1: GENDER, EYE COLOR, HAIR, HAIR COLOR (4 buttons)
        // Row 2: SKIN TONE, ACCESSORIES, FACIAL HAIR (3 buttons)
        val catBtnH = min(46f * density, lowerAreaHeight * 0.38f)
        val catBtnY1 = lowerAreaTop + 8f * density
        val catBtnY2 = catBtnY1 + catBtnH + 8f * density

        val row1Cols = 4
        val row1Spacing = 6f * density
        val row1BtnW = (containerW - (row1Cols - 1) * row1Spacing) / row1Cols
        val row1Cats = listOf(QuestionCategory.GENDER, QuestionCategory.EYE_COLOR, QuestionCategory.HAIR, QuestionCategory.HAIR_COLOR)

        row1Cats.forEachIndexed { i, cat ->
            val l = boardContainerRect.left + i * (row1BtnW + row1Spacing)
            categoryButtonRects[cat] = RectF(l, catBtnY1, l + row1BtnW, catBtnY1 + catBtnH)
        }

        val row2Cols = 3
        val row2Spacing = 8f * density
        val row2BtnW = (containerW - (row2Cols - 1) * row2Spacing) / row2Cols
        val row2Cats = listOf(QuestionCategory.SKIN_TONE, QuestionCategory.ACCESSORIES, QuestionCategory.FACIAL_HAIR)

        row2Cats.forEachIndexed { i, cat ->
            val l = boardContainerRect.left + i * (row2BtnW + row2Spacing)
            categoryButtonRects[cat] = RectF(l, catBtnY2, l + row2BtnW, catBtnY2 + catBtnH)
        }

        // 2. Answer Card Layout (Image 3, 4, 5)
        val ansCardH = min(68f * density, lowerAreaHeight * 0.50f)
        answerCardRect.set(boardContainerRect.left, lowerAreaTop + 4f * density, boardContainerRect.right, lowerAreaTop + 4f * density + ansCardH)

        // 3. Action Buttons below Answer Card (Undo + PASS/GUESS)
        val actionRowY = answerCardRect.bottom + 12f * density
        val actionRowH = min(50f * density, h - actionRowY - bottomMargin)

        val undoSize = actionRowH
        btnUndoRect.set(boardContainerRect.left, actionRowY, boardContainerRect.left + undoSize, actionRowY + actionRowH)

        val passW = min(150f * density, containerW * 0.45f)
        btnPassOrGuessRect.set((w - passW) / 2f, actionRowY, (w + passW) / 2f, actionRowY + actionRowH)

        // Secret confirmation button at bottom during pick phase
        val confirmW = min(220f * density, w * 0.70f)
        val confirmH = 46f * density
        val confirmY = h - 68f * density
        btnConfirmSecretRect.set((w - confirmW) / 2f, confirmY, (w + confirmW) / 2f, confirmY + confirmH)

        // Pass Phone Screen Layout (Reference Image 5)
        val passCardW = min(250f * density, w * 0.75f)
        val passCardH = 88f * density
        val passCardY = h * 0.74f
        passPhoneCardRect.set((w - passCardW) / 2f, passCardY, (w + passCardW) / 2f, passCardY + passCardH)

        val okBtnW = passCardW - 28f * density
        val okBtnH = 50f * density
        passPhoneOkBtnRect.set(
            passPhoneCardRect.centerX() - okBtnW / 2f,
            passPhoneCardRect.centerY() - okBtnH / 2f,
            passPhoneCardRect.centerX() + okBtnW / 2f,
            passPhoneCardRect.centerY() + okBtnH / 2f
        )

        // Sub-question dialog layout
        val sqW = min(w * 0.90f, 400f * density)
        val sqH = min(h * 0.65f, 440f * density)
        subQuestionDialogRect.set((w - sqW) / 2f, (h - sqH) / 2f, (w + sqW) / 2f, (h + sqH) / 2f)
        subQuestionCloseBtnRect.set(subQuestionDialogRect.right - 44f * density, subQuestionDialogRect.top + 10f * density, subQuestionDialogRect.right - 10f * density, subQuestionDialogRect.top + 44f * density)

        // Winner popup layout (Image 1)
        val winW = min(w * 0.88f, 380f * density)
        val winH = min(h * 0.42f, 320f * density)
        val winY = (h - winH) / 2f
        winnerCardRect.set((w - winW) / 2f, winY, (w + winW) / 2f, winY + winH)

        val boxW = (winW - 40f * density) / 2f
        val boxH = winH * 0.64f
        val boxY = winnerCardRect.top + 50f * density
        winnerGuessedBoxRect.set(winnerCardRect.left + 16f * density, boxY, winnerCardRect.left + 16f * density + boxW, boxY + boxH)
        winnerPickedBoxRect.set(winnerCardRect.right - 16f * density - boxW, boxY, winnerCardRect.right - 16f * density, boxY + boxH)

        val bannerH = 50f * density
        val bannerY = winnerCardRect.bottom + 16f * density
        winnerBannerRect.set(winnerCardRect.left, bannerY, winnerCardRect.right, bannerY + bannerH)
    }

    // ================= TOUCH INPUT =================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            if (isGameOver || isRoundEnding) return true

            when (currentPhase) {
                GamePhase.SELECT_P1_SECRET -> {
                    handleSecretSelectionTouch(p1Cards, x, y, p1SelectedSecretIndex) { newIndex ->
                        p1SelectedSecretIndex = newIndex
                    }
                    if (p1SelectedSecretIndex in roster.indices && btnConfirmSecretRect.contains(x, y)) {
                        p1SecretPerson = roster[p1SelectedSecretIndex]
                        soundManager.play(SoundManager.SoundType.START)
                        triggerVibration(40)

                        val p2 = players.getOrElse(1) { players[0] }
                        if (p2.isBot) {
                            p2SecretPerson = roster[random.nextInt(roster.size)]
                            currentPhase = GamePhase.TURN_QUESTION_PICK
                            activeTurnPlayerId = PlayerId.PLAYER_1
                        } else {
                            currentPhase = GamePhase.PASS_TO_P2_FOR_SECRET
                        }
                    }
                }
                GamePhase.PASS_TO_P2_FOR_SECRET -> {
                    if (passPhoneOkBtnRect.contains(x, y) || passPhoneCardRect.contains(x, y)) {
                        soundManager.play(SoundManager.SoundType.TAP)
                        currentPhase = GamePhase.SELECT_P2_SECRET
                    }
                }
                GamePhase.SELECT_P2_SECRET -> {
                    handleSecretSelectionTouch(p2Cards, x, y, p2SelectedSecretIndex) { newIndex ->
                        p2SelectedSecretIndex = newIndex
                    }
                    if (p2SelectedSecretIndex in roster.indices && btnConfirmSecretRect.contains(x, y)) {
                        p2SecretPerson = roster[p2SelectedSecretIndex]
                        soundManager.play(SoundManager.SoundType.START)
                        triggerVibration(40)
                        currentPhase = GamePhase.PASS_PHONE_BETWEEN_TURNS
                        activeTurnPlayerId = PlayerId.PLAYER_1
                    }
                }
                GamePhase.PASS_PHONE_BETWEEN_TURNS -> {
                    if (passPhoneOkBtnRect.contains(x, y) || passPhoneCardRect.contains(x, y)) {
                        soundManager.play(SoundManager.SoundType.TAP)
                        currentPhase = GamePhase.TURN_QUESTION_PICK
                        currentQuestionAsked = null
                        currentTurnFlippedCards.clear()
                    }
                }
                GamePhase.TURN_QUESTION_PICK -> {
                    handleQuestionPickTouch(x, y)
                }
                GamePhase.TURN_CATEGORY_POPUP -> {
                    handleCategoryPopupTouch(x, y)
                }
                GamePhase.TURN_ELIMINATION -> {
                    handleEliminationTouch(x, y)
                }
                GamePhase.WINNER_REVEAL_POPUP -> {
                    // Tap to advance to winner dialog
                    if (roundEndDelayTimer < 0f) {
                        endRound(pendingWinner)
                    }
                }
            }
        }
        return true
    }

    private fun handleSecretSelectionTouch(
        cards: List<CharacterCardState>,
        x: Float,
        y: Float,
        currentIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        cards.forEachIndexed { index, card ->
            if (card.rect.contains(x, y)) {
                onSelect(index)
                soundManager.play(SoundManager.SoundType.TAP)
                triggerVibration(20)
                return
            }
        }
    }

    private fun handleQuestionPickTouch(x: Float, y: Float) {
        val curPlayer = getCurrentTurnPlayer()
        if (curPlayer.isBot) return

        // Check if only 1 card is remaining; if so, tapping it or GUESS button makes final guess
        val currentCards = getActiveBoardCards()
        val activeCards = currentCards.filter { !it.isFlippedDown }
        if (activeCards.size == 1) {
            if (btnPassOrGuessRect.contains(x, y) || activeCards.first().rect.contains(x, y)) {
                triggerFinalGuess(activeCards.first().character)
                return
            }
        }

        // Tap a Category button (Image 2)
        for ((cat, rect) in categoryButtonRects) {
            if (rect.contains(x, y)) {
                questionWarningTimer = 0f
                selectedCategory = cat
                computeSubQuestionsForCategory(cat)
                currentPhase = GamePhase.TURN_CATEGORY_POPUP
                soundManager.play(SoundManager.SoundType.TAP)
                return
            }
        }

        // If player taps directly on any card or board before asking a question:
        val touchedCard = currentCards.firstOrNull { it.rect.contains(x, y) }
        if (touchedCard != null || boardContainerRect.contains(x, y)) {
            questionWarningTimer = 2.5f
            soundManager.play(SoundManager.SoundType.BOUNCE)
            triggerVibration(40)
            return
        }
    }

    private fun handleCategoryPopupTouch(x: Float, y: Float) {
        if (subQuestionCloseBtnRect.contains(x, y)) {
            currentPhase = GamePhase.TURN_QUESTION_PICK
            selectedCategory = null
            soundManager.play(SoundManager.SoundType.TAP)
            return
        }

        // Tap a sub-question
        for ((option, rect) in subQuestionButtonRects) {
            if (rect.contains(x, y)) {
                askQuestion(option)
                currentPhase = GamePhase.TURN_ELIMINATION
                selectedCategory = null
                return
            }
        }
    }

    private fun handleEliminationTouch(x: Float, y: Float) {
        val curPlayer = getCurrentTurnPlayer()
        if (curPlayer.isBot) return

        val currentCards = getActiveBoardCards()
        val activeCards = currentCards.filter { !it.isFlippedDown }

        // 1. Tap card to toggle flip
        currentCards.forEach { card ->
            if (card.rect.contains(x, y)) {
                card.isFlippedDown = !card.isFlippedDown
                if (card.isFlippedDown) {
                    currentTurnFlippedCards.add(card)
                } else {
                    currentTurnFlippedCards.remove(card)
                }
                soundManager.play(SoundManager.SoundType.TAP)
                triggerVibration(25)
                return
            }
        }

        // 2. Undo button ↶
        if (btnUndoRect.contains(x, y)) {
            val last = currentTurnFlippedCards.removeLastOrNull()
            if (last != null) {
                last.isFlippedDown = false
                soundManager.play(SoundManager.SoundType.TAP)
                triggerVibration(20)
            }
            return
        }

        // 3. PASS or GUESS button
        if (btnPassOrGuessRect.contains(x, y)) {
            val remaining = currentCards.count { !it.isFlippedDown }
            if (remaining == 1) {
                // Exactly 1 card left -> Trigger GUESS (Image 5)
                val lastCard = currentCards.first { !it.isFlippedDown }
                triggerFinalGuess(lastCard.character)
            } else {
                // Pass turn to next player (Image 4)
                soundManager.play(SoundManager.SoundType.TAP)
                passTurn()
            }
            return
        }
    }

    private fun askQuestion(option: QuestionOption) {
        val opponentSecret = (if (activeTurnPlayerId == PlayerId.PLAYER_1) p2SecretPerson else p1SecretPerson) ?: return
        val answer = option.test(opponentSecret)

        currentQuestionAsked = option
        currentQuestionAnswer = answer
        currentTurnFlippedCards.clear()

        if (answer) {
            soundManager.play(SoundManager.SoundType.GOAL)
        } else {
            soundManager.play(SoundManager.SoundType.BOUNCE)
        }
        triggerVibration(45)
    }

    private fun triggerFinalGuess(guessedPerson: PersonCharacter) {
        val opponentSecret = if (activeTurnPlayerId == PlayerId.PLAYER_1) p2SecretPerson else p1SecretPerson ?: roster.first()
        val curPlayer = getCurrentTurnPlayer()

        winnerGuessedPerson = guessedPerson
        winnerActualPerson = opponentSecret
        winnerDisplayName = if (curPlayer.id == PlayerId.PLAYER_1) "RED" else "BLUE"

        if (guessedPerson.id == opponentSecret?.id) {
            // Correct Guess!
            curPlayer.score = 1
            pendingWinner = curPlayer
            soundManager.play(SoundManager.SoundType.VICTORY)
            particles.spawnConfetti(width, height, listOf(curPlayer.id.color, Color.parseColor("#FFD700")), 100)
        } else {
            // Incorrect guess -> Opponent wins!
            val oppPlayer = players.firstOrNull { it.id != curPlayer.id } ?: curPlayer
            oppPlayer.score = 1
            pendingWinner = oppPlayer
            winnerDisplayName = if (oppPlayer.id == PlayerId.PLAYER_1) "RED" else "BLUE"
            soundManager.play(SoundManager.SoundType.BOUNCE)
        }

        currentPhase = GamePhase.WINNER_REVEAL_POPUP
        roundEndDelayTimer = 2.8f
    }

    private fun passTurn() {
        questionWarningTimer = 0f
        val p2 = players.getOrElse(1) { players[0] }
        if (p2.isBot) {
            activeTurnPlayerId = if (activeTurnPlayerId == PlayerId.PLAYER_1) PlayerId.PLAYER_2 else PlayerId.PLAYER_1
            currentQuestionAsked = null
            currentTurnFlippedCards.clear()

            if (activeTurnPlayerId == PlayerId.PLAYER_2) {
                botThinkingTimer = 1.2f
                botIsActing = false
            } else {
                currentPhase = GamePhase.TURN_QUESTION_PICK
            }
        } else {
            activeTurnPlayerId = if (activeTurnPlayerId == PlayerId.PLAYER_1) PlayerId.PLAYER_2 else PlayerId.PLAYER_1
            currentPhase = GamePhase.PASS_PHONE_BETWEEN_TURNS
        }
    }

    private fun computeSubQuestionsForCategory(cat: QuestionCategory) {
        subQuestionButtonRects.clear()
        val options = getQuestionsForCategory(cat)
        val dialogW = subQuestionDialogRect.width()
        val margin = 14f * density
        val btnW = dialogW - margin * 2
        val btnH = 44f * density
        val startY = subQuestionDialogRect.top + 62f * density

        options.forEachIndexed { i, opt ->
            val top = startY + i * (btnH + 8f * density)
            subQuestionButtonRects.add(Pair(opt, RectF(subQuestionDialogRect.left + margin, top, subQuestionDialogRect.left + margin + btnW, top + btnH)))
        }
    }

    private fun getQuestionsForCategory(cat: QuestionCategory): List<QuestionOption> {
        return when (cat) {
            QuestionCategory.GENDER -> listOf(
                QuestionOption(cat, "Is the person female?", "👧") { it.gender == PersonGender.FEMALE },
                QuestionOption(cat, "Is the person male?", "👦") { it.gender == PersonGender.MALE }
            )
            QuestionCategory.EYE_COLOR -> listOf(
                QuestionOption(cat, "Does the person have blue eyes?", "🔵") { it.eyeColor == PersonEyeColor.BLUE },
                QuestionOption(cat, "Does the person have green eyes?", "🟢") { it.eyeColor == PersonEyeColor.GREEN },
                QuestionOption(cat, "Does the person have brown eyes?", "🟤") { it.eyeColor == PersonEyeColor.BROWN }
            )
            QuestionCategory.HAIR -> listOf(
                QuestionOption(cat, "Does the person have short hair?", "✂️") { it.hairLength == PersonHairLength.SHORT },
                QuestionOption(cat, "Does the person have long hair?", "💇") { it.hairLength == PersonHairLength.LONG },
                QuestionOption(cat, "Is the person bald or buzzcut?", "🧑‍🦲") { it.hairLength == PersonHairLength.BALD }
            )
            QuestionCategory.HAIR_COLOR -> listOf(
                QuestionOption(cat, "Does the person have blonde hair?", "👱") { it.hairColor == PersonHairColor.BLONDE },
                QuestionOption(cat, "Does the person have black hair?", "⚫") { it.hairColor == PersonHairColor.BLACK },
                QuestionOption(cat, "Does the person have brown hair?", "🟤") { it.hairColor == PersonHairColor.BROWN },
                QuestionOption(cat, "Does the person have red hair?", "🔴") { it.hairColor == PersonHairColor.RED },
                QuestionOption(cat, "Does the person have grey hair?", "🦳") { it.hairColor == PersonHairColor.GREY }
            )
            QuestionCategory.SKIN_TONE -> listOf(
                QuestionOption(cat, "Does the person have light skin?", "🏻") { it.skinTone == PersonSkinTone.LIGHT },
                QuestionOption(cat, "Does the person have medium skin?", "🏽") { it.skinTone == PersonSkinTone.MEDIUM },
                QuestionOption(cat, "Does the person have dark skin?", "🏿") { it.skinTone == PersonSkinTone.DARK }
            )
            QuestionCategory.ACCESSORIES -> listOf(
                QuestionOption(cat, "Does the person wear glasses?", "👓") { it.hasGlasses },
                QuestionOption(cat, "Does the person wear a hat?", "🧢") { it.hasHat },
                QuestionOption(cat, "Does the person wear earrings?", "💎") { it.hasEarrings }
            )
            QuestionCategory.FACIAL_HAIR -> listOf(
                QuestionOption(cat, "Does the person have a beard?", "🧔") { it.hasBeard },
                QuestionOption(cat, "Does the person have a mustache?", "👨") { it.hasMustache },
                QuestionOption(cat, "Is the person clean-shaven?", "🪒") { !it.hasBeard && !it.hasMustache }
            )
        }
    }

    private fun getAllAvailableQuestions(): List<QuestionOption> {
        return QuestionCategory.values().flatMap { getQuestionsForCategory(it) }
    }

    // ================= LOOP UPDATE =================

    override fun update(deltaTime: Float) {
        val dt = deltaTime.coerceIn(0.001f, 0.05f)
        particles.update()

        if (questionWarningTimer > 0f) {
            questionWarningTimer = max(0f, questionWarningTimer - dt)
        }

        if (roundEndDelayTimer >= 0f) {
            roundEndDelayTimer -= dt
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                isRoundEnding = false
                endRound(pendingWinner)
            }
            return
        }

        if (isGameOver || isRoundEnding) return

        // Bot AI
        if (activeTurnPlayerId == PlayerId.PLAYER_2 && players.getOrElse(1) { players[0] }.isBot) {
            updateBotAI(dt)
        }
    }

    private fun updateBotAI(dt: Float) {
        if (botThinkingTimer > 0f) {
            botThinkingTimer -= dt
            if (botThinkingTimer <= 0f && !botIsActing) {
                botIsActing = true
                executeBotTurnAction()
            }
        }
    }

    private fun executeBotTurnAction() {
        val botBoard = p2Cards
        val remainingSuspects = botBoard.filter { !it.isFlippedDown }

        if (remainingSuspects.size <= 1) {
            val guessPerson = remainingSuspects.firstOrNull()?.character ?: roster.first()
            triggerFinalGuess(guessPerson)
            return
        }

        val allQuestions = getAllAvailableQuestions().filter { !botAskedQuestions.contains(it.label) }
        val bestQuestion = if (botDifficulty == 0 && random.nextFloat() < 0.45f) {
            allQuestions.randomOrNull() ?: allQuestions.firstOrNull()
        } else if (botDifficulty == 1 && random.nextFloat() < 0.20f) {
            allQuestions.randomOrNull() ?: allQuestions.firstOrNull()
        } else {
            allQuestions.minByOrNull { q ->
                val matchCount = remainingSuspects.count { q.test(it.character) }
                abs(matchCount - (remainingSuspects.size / 2.0))
            } ?: allQuestions.firstOrNull()
        }

        if (bestQuestion != null) {
            botAskedQuestions.add(bestQuestion.label)
            val p1Secret = p1SecretPerson ?: roster.first()
            val answer = bestQuestion.test(p1Secret)

            currentQuestionAsked = bestQuestion
            currentQuestionAnswer = answer

            botBoard.forEach { card ->
                if (!card.isFlippedDown) {
                    val matchesTrait = bestQuestion.test(card.character)
                    if (matchesTrait != answer) {
                        card.isFlippedDown = true
                    }
                }
            }

            if (answer) soundManager.play(SoundManager.SoundType.GOAL)
            else soundManager.play(SoundManager.SoundType.BOUNCE)

            passTurn()
        }
    }

    private fun getCurrentTurnPlayer(): Player {
        return players.firstOrNull { it.id == activeTurnPlayerId } ?: players[0]
    }

    private fun getActiveBoardCards(): List<CharacterCardState> {
        return if (activeTurnPlayerId == PlayerId.PLAYER_1) p1Cards else p2Cards
    }

    // ================= RENDERING (COSMIC DARK NEON THEME) =================

    private val bgCosmicVoid = Color.parseColor("#090B16")
    private val boardCardBg = Color.parseColor("#111424")
    private val boardCardBorder = Color.parseColor("#262D4A")
    private val tileNeutralBg = Color.parseColor("#181D33")
    private val tileNeutralBorder = Color.parseColor("#283152")
    private val tileInactiveBg = Color.parseColor("#0B0E1B")
    private val tileInactiveBorder = Color.parseColor("#161B2C")

    override fun render(canvas: Canvas) {
        // 1. Cosmic Void Background
        canvas.drawColor(bgCosmicVoid)

        // Ambient cyber dot grid
        renderAmbientCosmicGrid(canvas)

        when (currentPhase) {
            GamePhase.SELECT_P1_SECRET -> {
                renderSecretPickScreen(canvas, p1Cards, p1SelectedSecretIndex)
            }
            GamePhase.PASS_TO_P2_FOR_SECRET -> {
                renderPassPhoneScreen(canvas, PlayerId.PLAYER_2)
            }
            GamePhase.SELECT_P2_SECRET -> {
                renderSecretPickScreen(canvas, p2Cards, p2SelectedSecretIndex)
            }
            GamePhase.PASS_PHONE_BETWEEN_TURNS -> {
                renderPassPhoneScreen(canvas, activeTurnPlayerId)
            }
            GamePhase.TURN_QUESTION_PICK -> {
                renderQuestionPickScreen(canvas)
            }
            GamePhase.TURN_CATEGORY_POPUP -> {
                renderQuestionPickScreen(canvas)
                renderSubQuestionPopup(canvas)
            }
            GamePhase.TURN_ELIMINATION -> {
                renderEliminationScreen(canvas)
            }
            GamePhase.WINNER_REVEAL_POPUP -> {
                renderWinnerRevealScreen(canvas)
            }
        }

        particles.draw(canvas)
    }

    private fun renderAmbientCosmicGrid(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B2238")
        val cols = 7
        val rows = 14
        val stepX = width / cols
        val stepY = height / rows

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                canvas.drawCircle(i * stepX + stepX * 0.5f, j * stepY + stepY * 0.5f, 1.5f * density, paint)
            }
        }
    }

    // ================= BOARD CONTAINER RENDERING =================

    private fun renderBoard(canvas: Canvas, cards: List<CharacterCardState>, selectedIdx: Int = -1) {
        // Frosted chassis card with cosmic border
        paint.style = Paint.Style.FILL
        paint.color = boardCardBg
        canvas.drawRoundRect(boardContainerRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = boardCardBorder
        canvas.drawRoundRect(boardContainerRect, 14f * density, 14f * density, paint)

        cards.forEachIndexed { index, card ->
            val isSelected = (index == selectedIdx)
            renderCard(canvas, card, isSelected)
        }
    }

    private fun renderCard(canvas: Canvas, card: CharacterCardState, isSelected: Boolean) {
        val rect = card.rect
        val c = card.character
        val r = 6f * density

        if (card.isFlippedDown) {
            // Face-down eliminated card: Recessed deep dark tile
            paint.style = Paint.Style.FILL
            paint.color = tileInactiveBg
            canvas.drawRoundRect(rect, r, r, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = tileInactiveBorder
            canvas.drawRoundRect(rect, r, r, paint)
            return
        }

        // Draw authentic character face sprite matching user reference with crisp filtering
        val bmp = characterBitmaps[c.id]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        } else {
            // Fallback: draw base tile with name
            paint.style = Paint.Style.FILL
            paint.color = tileNeutralBg
            canvas.drawRoundRect(rect, r, r, paint)

            val nameH = rect.height() * 0.22f
            val nameRect = RectF(rect.left, rect.bottom - nameH, rect.right, rect.bottom)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0E1220")
            canvas.drawRoundRect(nameRect, r, r, paint)
            canvas.drawRect(nameRect.left, nameRect.top, nameRect.right, nameRect.top + r, paint)

            textPaint.textSize = 9.5f * density
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            val ty = nameRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(c.name, nameRect.centerX(), ty, textPaint)

            val avatarRect = RectF(rect.left, rect.top, rect.right, rect.bottom - nameH)
            drawAvatar(canvas, c, avatarRect)
        }

        // Selected neon outline
        if (isSelected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = Color.parseColor("#00E5FF") // Radiant neon cyan
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    // ================= SCREEN: SECRET SELECTION =================

    private fun renderSecretPickScreen(canvas: Canvas, cards: List<CharacterCardState>, selectedIdx: Int) {
        renderBoard(canvas, cards, selectedIdx)

        if (selectedIdx in roster.indices) {
            // Vibrant neon green confirm button
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#00E676")
            canvas.drawRoundRect(btnConfirmSecretRect, 16f * density, 16f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.parseColor("#69F0AE")
            canvas.drawRoundRect(btnConfirmSecretRect, 16f * density, 16f * density, paint)

            textPaint.textSize = 17f * density
            textPaint.color = Color.parseColor("#090B16")
            textPaint.textAlign = Paint.Align.CENTER
            val ty = btnConfirmSecretRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("OK, I'M ${roster[selectedIdx].name.uppercase()} ➔", btnConfirmSecretRect.centerX(), ty, textPaint)
        } else {
            textPaint.textSize = 22f * density
            textPaint.color = Color.parseColor("#00E5FF")
            textPaint.textAlign = Paint.Align.CENTER
            val ty = btnConfirmSecretRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("Choose your character!", width * 0.5f, ty, textPaint)
        }
    }

    // ================= SCREEN: PASS PHONE =================

    private fun renderPassPhoneScreen(canvas: Canvas, targetPlayerId: PlayerId) {
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f * density

        val pName = if (targetPlayerId == PlayerId.PLAYER_1) "Player 1" else "friend"
        val centerY = height * 0.40f
        val lineSpacing = 38f * density

        canvas.drawText("Pass the screen", width * 0.5f, centerY - lineSpacing, textPaint)
        textPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText("to your $pName", width * 0.5f, centerY, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("and don't look", width * 0.5f, centerY + lineSpacing, textPaint)

        // Frosted glass chassis with Green OK button
        paint.style = Paint.Style.FILL
        paint.color = boardCardBg
        canvas.drawRoundRect(passPhoneCardRect, 20f * density, 20f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = boardCardBorder
        canvas.drawRoundRect(passPhoneCardRect, 20f * density, 20f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00E676")
        canvas.drawRoundRect(passPhoneOkBtnRect, 14f * density, 14f * density, paint)

        textPaint.textSize = 20f * density
        textPaint.color = Color.parseColor("#090B16")
        val textY = passPhoneOkBtnRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("OK", passPhoneOkBtnRect.centerX(), textY, textPaint)
    }

    // ================= SCREEN: QUESTION PICK =================

    private fun renderQuestionPickScreen(canvas: Canvas) {
        val currentCards = getActiveBoardCards()
        renderBoard(canvas, currentCards)

        val activeCount = currentCards.count { !it.isFlippedDown }

        if (activeCount == 1) {
            // Only 1 card remains -> Vibrant Neon Green GUESS button
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#00E676")
            canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.parseColor("#B9F6CA")
            canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

            textPaint.textSize = 17f * density
            textPaint.color = Color.parseColor("#090B16")
            textPaint.textAlign = Paint.Align.CENTER
            val ty = btnPassOrGuessRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("GUESS", btnPassOrGuessRect.centerX(), ty, textPaint)
            return
        }

        val isWarning = questionWarningTimer > 0f
        val pulse = if (isWarning) (sin(questionWarningTimer * 10f) * 0.5f + 0.5f) else 0f

        // Render 7 Dark Glass Category Buttons
        for ((cat, rect) in categoryButtonRects) {
            paint.style = Paint.Style.FILL
            paint.color = tileNeutralBg
            canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

            paint.style = Paint.Style.STROKE
            if (isWarning) {
                paint.strokeWidth = 2f * density
                paint.color = Color.argb((160 + 95 * pulse).toInt(), 0, 229, 255)
            } else {
                paint.strokeWidth = 1f * density
                paint.color = tileNeutralBorder
            }
            canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

            // Category Icon
            textPaint.textSize = 16f * density
            textPaint.textAlign = Paint.Align.CENTER
            val iconY = rect.top + rect.height() * 0.44f
            canvas.drawText(cat.icon, rect.centerX(), iconY, textPaint)

            // Category Title
            textPaint.textSize = 9.5f * density
            textPaint.color = Color.WHITE
            val titleY = rect.bottom - rect.height() * 0.16f
            canvas.drawText(cat.title, rect.centerX(), titleY, textPaint)
        }

        if (questionWarningTimer > 0f) {
            renderQuestionRequiredWarning(canvas)
        }
    }

    private fun renderQuestionRequiredWarning(canvas: Canvas) {
        val alphaNorm = when {
            questionWarningTimer > 2.1f -> (2.5f - questionWarningTimer) / 0.4f
            questionWarningTimer < 0.4f -> questionWarningTimer / 0.4f
            else -> 1f
        }.coerceIn(0f, 1f)

        val alphaInt = (alphaNorm * 255).toInt()
        if (alphaInt <= 0) return

        val bannerW = min(boardContainerRect.width() * 0.94f, 340f * density)
        val bannerH = 56f * density
        val bannerX = (width - bannerW) / 2f
        val bannerY = boardContainerRect.bottom - bannerH - 12f * density
        val bannerRect = RectF(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH)

        // Backdrop chassis
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((alphaNorm * 245).toInt(), 14, 18, 34)
        canvas.drawRoundRect(bannerRect, 14f * density, 14f * density, paint)

        // Neon Amber Glowing Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.argb(alphaInt, 255, 179, 0)
        canvas.drawRoundRect(bannerRect, 14f * density, 14f * density, paint)

        // Warning Icon
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 18f * density
        val iconX = bannerRect.left + 14f * density
        val iconY = bannerRect.centerY() + 6f * density
        canvas.drawText("⚠️", iconX, iconY, textPaint)

        // Line 1: Primary message
        textPaint.textSize = 13.5f * density
        textPaint.color = Color.argb(alphaInt, 255, 255, 255)
        val textLeft = iconX + 28f * density
        val line1Y = bannerRect.top + 23f * density
        canvas.drawText("First select a question below!", textLeft, line1Y, textPaint)

        // Line 2: Secondary guidance
        textPaint.textSize = 11f * density
        textPaint.color = Color.argb((alphaNorm * 225).toInt(), 0, 229, 255)
        val line2Y = bannerRect.bottom - 13f * density
        canvas.drawText("Tap a category to ask, then eliminate cards.", textLeft, line2Y, textPaint)
    }

    // ================= MODAL: SUB-QUESTION POPUP =================

    private fun renderSubQuestionPopup(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#D9080B14")
        canvas.drawRect(0f, 0f, width, height, paint)

        // Frosted Glass Modal Box
        paint.color = boardCardBg
        canvas.drawRoundRect(subQuestionDialogRect, 20f * density, 20f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#00E5FF") // Neon cyan outline
        canvas.drawRoundRect(subQuestionDialogRect, 20f * density, 20f * density, paint)

        // Close ✕ button
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E253E")
        canvas.drawCircle(subQuestionCloseBtnRect.centerX(), subQuestionCloseBtnRect.centerY(), 16f * density, paint)

        textPaint.textSize = 16f * density
        textPaint.color = Color.parseColor("#00E5FF")
        textPaint.textAlign = Paint.Align.CENTER
        var ty = subQuestionCloseBtnRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("✕", subQuestionCloseBtnRect.centerX(), ty, textPaint)

        // Title
        val cat = selectedCategory ?: QuestionCategory.GENDER
        textPaint.textSize = 16f * density
        textPaint.color = Color.WHITE
        ty = subQuestionDialogRect.top + 34f * density
        canvas.drawText("${cat.icon}  ${cat.title}", subQuestionDialogRect.centerX(), ty, textPaint)

        // Sub-question buttons
        for ((opt, rect) in subQuestionButtonRects) {
            paint.style = Paint.Style.FILL
            paint.color = tileNeutralBg
            canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = tileNeutralBorder
            canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

            textPaint.textSize = 13f * density
            textPaint.color = Color.WHITE
            ty = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("${opt.iconEmoji}  ${opt.label}", rect.centerX(), ty, textPaint)
        }
    }

    // ================= SCREEN: ELIMINATION =================

    private fun renderEliminationScreen(canvas: Canvas) {
        val currentCards = getActiveBoardCards()
        renderBoard(canvas, currentCards)

        val q = currentQuestionAsked ?: return
        val isYes = currentQuestionAnswer

        // 1. Frosted Glass Answer Card
        paint.style = Paint.Style.FILL
        paint.color = boardCardBg
        canvas.drawRoundRect(answerCardRect, 16f * density, 16f * density, paint)

        val ansColor = if (isYes) Color.parseColor("#00E676") else Color.parseColor("#FF1744")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = ansColor
        canvas.drawRoundRect(answerCardRect, 16f * density, 16f * density, paint)

        // Question Text (Crisp white, centered)
        textPaint.textSize = 16f * density
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        val qY = answerCardRect.top + answerCardRect.height() * 0.42f
        canvas.drawText(q.label, answerCardRect.centerX(), qY, textPaint)

        // Answer Line: Icon + Neon Yes! / Red No!
        val ansStr = if (isYes) "Yes!" else "No!"
        textPaint.textSize = 18f * density
        textPaint.color = ansColor
        val aY = answerCardRect.bottom - answerCardRect.height() * 0.18f
        canvas.drawText("${q.iconEmoji}  $ansStr", answerCardRect.centerX(), aY, textPaint)

        // 2. Action Area below Answer Card
        val activeCount = currentCards.count { !it.isFlippedDown }
        val hasFlippedThisTurn = currentTurnFlippedCards.isNotEmpty()

        if (!hasFlippedThisTurn && activeCount > 1) {
            // Help text
            textPaint.textSize = 11.5f * density
            textPaint.color = Color.parseColor("#8E99B3")
            textPaint.textAlign = Paint.Align.CENTER
            val helpY = answerCardRect.bottom + 24f * density
            canvas.drawText("HELP: Tap the cards to remove candidates that", width * 0.5f, helpY, textPaint)
            canvas.drawText("do not match the question's trait.", width * 0.5f, helpY + 16f * density, textPaint)
        } else {
            // Render Dark Glass Undo Button ↶
            paint.style = Paint.Style.FILL
            paint.color = tileNeutralBg
            canvas.drawCircle(btnUndoRect.centerX(), btnUndoRect.centerY(), btnUndoRect.width() * 0.45f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = tileNeutralBorder
            canvas.drawCircle(btnUndoRect.centerX(), btnUndoRect.centerY(), btnUndoRect.width() * 0.45f, paint)

            textPaint.textSize = 20f * density
            textPaint.color = Color.parseColor("#00E5FF")
            val tyUndo = btnUndoRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("↶", btnUndoRect.centerX(), tyUndo, textPaint)

            // Render PASS or GUESS Button
            if (activeCount == 1) {
                // Exactly 1 card left -> Neon Green GUESS Button
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#00E676")
                canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                paint.color = Color.parseColor("#B9F6CA")
                canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

                textPaint.textSize = 17f * density
                textPaint.color = Color.parseColor("#090B16")
                val ty = btnPassOrGuessRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText("GUESS", btnPassOrGuessRect.centerX(), ty, textPaint)
            } else {
                // More than 1 card left -> Dark Glass PASS Button
                paint.style = Paint.Style.FILL
                paint.color = tileNeutralBg
                canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                paint.color = tileNeutralBorder
                canvas.drawRoundRect(btnPassOrGuessRect, 14f * density, 14f * density, paint)

                textPaint.textSize = 16f * density
                textPaint.color = Color.WHITE
                val ty = btnPassOrGuessRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText("PASS", btnPassOrGuessRect.centerX(), ty, textPaint)
            }
        }
    }

    // ================= SCREEN: WINNER REVEAL =================

    private fun renderWinnerRevealScreen(canvas: Canvas) {
        val currentCards = getActiveBoardCards()
        renderBoard(canvas, currentCards)

        val guessed = winnerGuessedPerson ?: roster.first()
        val picked = winnerActualPerson ?: roster.first()

        // Dim background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#D9080B14")
        canvas.drawRect(0f, 0f, width, height, paint)

        // Frosted Glass Card
        paint.color = boardCardBg
        canvas.drawRoundRect(winnerCardRect, 22f * density, 22f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.parseColor("#8A2BE2") // Purple neon border
        canvas.drawRoundRect(winnerCardRect, 22f * density, 22f * density, paint)
        paint.style = Paint.Style.FILL

        // Left Column: "You guessed"
        textPaint.textSize = 15f * density
        textPaint.color = Color.parseColor("#FF7043")
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("You guessed", winnerGuessedBoxRect.centerX(), winnerCardRect.top + 34f * density, textPaint)

        paint.color = Color.parseColor("#261414")
        canvas.drawRoundRect(winnerGuessedBoxRect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = Color.parseColor("#FF5722")
        canvas.drawRoundRect(winnerGuessedBoxRect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.FILL

        val innerGuessed = RectF(winnerGuessedBoxRect.left + 8f * density, winnerGuessedBoxRect.top + 8f * density, winnerGuessedBoxRect.right - 8f * density, winnerGuessedBoxRect.bottom - 8f * density)
        renderMiniAvatarCard(canvas, guessed, innerGuessed)

        // Right Column: "Opponent picked"
        textPaint.textSize = 15f * density
        textPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText("Opponent picked", winnerPickedBoxRect.centerX(), winnerCardRect.top + 34f * density, textPaint)

        paint.color = Color.parseColor("#0F1E2E")
        canvas.drawRoundRect(winnerPickedBoxRect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(winnerPickedBoxRect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.FILL

        val innerPicked = RectF(winnerPickedBoxRect.left + 8f * density, winnerPickedBoxRect.top + 8f * density, winnerPickedBoxRect.right - 8f * density, winnerPickedBoxRect.bottom - 8f * density)
        renderMiniAvatarCard(canvas, picked, innerPicked)

        // Winner Banner at bottom
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#161D32")
        canvas.drawRoundRect(winnerBannerRect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#283152")
        canvas.drawRoundRect(winnerBannerRect, 12f * density, 12f * density, paint)

        textPaint.textSize = 20f * density
        textPaint.color = Color.parseColor("#FFD700")
        val ty = winnerBannerRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("$winnerDisplayName WINS! 👑", winnerBannerRect.centerX(), ty, textPaint)
    }

    private fun renderMiniAvatarCard(canvas: Canvas, c: PersonCharacter, rect: RectF) {
        val bmp = characterBitmaps[c.id]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        } else {
            val r = 10f * density
            paint.style = Paint.Style.FILL
            paint.color = tileNeutralBg
            canvas.drawRoundRect(rect, r, r, paint)

            val nameH = rect.height() * 0.22f
            val nameRect = RectF(rect.left, rect.bottom - nameH, rect.right, rect.bottom)
            paint.color = Color.parseColor("#0E1220")
            canvas.drawRoundRect(nameRect, r, r, paint)
            canvas.drawRect(nameRect.left, nameRect.top, nameRect.right, nameRect.top + r, paint)

            textPaint.textSize = 10f * density
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            val ty = nameRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(c.name, nameRect.centerX(), ty, textPaint)

            val avRect = RectF(rect.left, rect.top, rect.right, rect.bottom - nameH)
            drawAvatar(canvas, c, avRect)
        }
    }

    // ================= AVATAR VECTOR DRAWING =================

    private fun drawAvatar(canvas: Canvas, c: PersonCharacter, bounds: RectF) {
        val cx = bounds.centerX()
        val cy = bounds.centerY() + 2f * density
        val faceR = min(bounds.width(), bounds.height()) * 0.32f

        val skinColor = when (c.skinTone) {
            PersonSkinTone.LIGHT -> Color.parseColor("#FCD5B5")
            PersonSkinTone.MEDIUM -> Color.parseColor("#D49A6A")
            PersonSkinTone.DARK -> Color.parseColor("#7D4E2D")
        }

        val hairColor = when (c.hairColor) {
            PersonHairColor.BLONDE -> Color.parseColor("#F4D03F")
            PersonHairColor.BROWN -> Color.parseColor("#5D4037")
            PersonHairColor.BLACK -> Color.parseColor("#212121")
            PersonHairColor.RED -> Color.parseColor("#E65100")
            PersonHairColor.GREY -> Color.parseColor("#ECEFF1")
        }

        // 1. Back Hair (Long styles)
        if (c.hairLength == PersonHairLength.LONG) {
            paint.style = Paint.Style.FILL
            paint.color = hairColor
            val hairRect = RectF(cx - faceR * 1.35f, cy - faceR * 1.1f, cx + faceR * 1.35f, cy + faceR * 1.4f)
            canvas.drawRoundRect(hairRect, faceR * 0.8f, faceR * 0.8f, paint)
        }

        // 2. Collar
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4A90E2")
        val collarRect = RectF(cx - faceR * 0.95f, cy + faceR * 0.7f, cx + faceR * 0.95f, bounds.bottom)
        canvas.drawRoundRect(collarRect, 8f * density, 8f * density, paint)

        // 3. Face
        paint.color = skinColor
        canvas.drawCircle(cx, cy, faceR, paint)

        // 4. Eyes
        val eyeColor = when (c.eyeColor) {
            PersonEyeColor.BLUE -> Color.parseColor("#1E88E5")
            PersonEyeColor.GREEN -> Color.parseColor("#43A047")
            PersonEyeColor.BROWN -> Color.parseColor("#4E342E")
        }
        val eyeSpacing = faceR * 0.44f
        val eyeY = cy - faceR * 0.08f
        val eyeR = faceR * 0.15f

        paint.color = Color.WHITE
        canvas.drawCircle(cx - eyeSpacing, eyeY, eyeR, paint)
        canvas.drawCircle(cx + eyeSpacing, eyeY, eyeR, paint)

        paint.color = eyeColor
        canvas.drawCircle(cx - eyeSpacing, eyeY, eyeR * 0.65f, paint)
        canvas.drawCircle(cx + eyeSpacing, eyeY, eyeR * 0.65f, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(cx - eyeSpacing, eyeY, eyeR * 0.32f, paint)
        canvas.drawCircle(cx + eyeSpacing, eyeY, eyeR * 0.32f, paint)

        // 5. Front Hair / Bangs
        if (c.hairLength != PersonHairLength.BALD && !c.hasHat) {
            paint.color = hairColor
            val topHair = RectF(cx - faceR * 1.05f, cy - faceR * 1.25f, cx + faceR * 1.05f, cy - faceR * 0.2f)
            canvas.drawRoundRect(topHair, faceR * 0.6f, faceR * 0.6f, paint)
        }

        // 6. Hats
        if (c.hasHat) {
            paint.color = when (c.hatType) {
                HatType.FEDORA -> Color.parseColor("#6D4C41")
                HatType.BEANIE -> Color.parseColor("#8E24AA")
                HatType.BERET -> Color.parseColor("#D81B60")
                HatType.BUCKET -> Color.parseColor("#A1887F")
                else -> Color.parseColor("#E53935")
            }
            val hatRect = RectF(cx - faceR * 1.25f, cy - faceR * 1.35f, cx + faceR * 1.25f, cy - faceR * 0.25f)
            canvas.drawRoundRect(hatRect, faceR * 0.5f, faceR * 0.5f, paint)
        }

        // 7. Glasses
        if (c.hasGlasses) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = if (c.name == "Ben") Color.parseColor("#FDD835") else Color.parseColor("#263238")

            val glassR = eyeR * 1.35f
            canvas.drawCircle(cx - eyeSpacing, eyeY, glassR, paint)
            canvas.drawCircle(cx + eyeSpacing, eyeY, glassR, paint)
            canvas.drawLine(cx - eyeSpacing + glassR, eyeY, cx + eyeSpacing - glassR, eyeY, paint)
        }

        // 8. Facial Hair
        if (c.hasMustache || c.hasBeard) {
            paint.style = Paint.Style.FILL
            paint.color = hairColor
            if (c.hasMustache) {
                val mRect = RectF(cx - faceR * 0.40f, cy + faceR * 0.25f, cx + faceR * 0.40f, cy + faceR * 0.42f)
                canvas.drawRoundRect(mRect, 4f * density, 4f * density, paint)
            }
            if (c.hasBeard) {
                val bRect = RectF(cx - faceR * 0.55f, cy + faceR * 0.45f, cx + faceR * 0.55f, cy + faceR * 0.95f)
                canvas.drawRoundRect(bRect, faceR * 0.3f, faceR * 0.3f, paint)
            }
        }

        // 9. Earrings
        if (c.hasEarrings) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFD700")
            canvas.drawCircle(cx - faceR * 1.05f, cy + faceR * 0.20f, 3.5f * density, paint)
            canvas.drawCircle(cx + faceR * 1.05f, cy + faceR * 0.20f, 3.5f * density, paint)
        }
    }

    override fun shouldShowPlayerScoresOnWinnerDialog(): Boolean = false
    override fun getPlayerScoreString(player: Player): String = if (player.score == 1) "Unmasked Rival! 👑" else "Eliminated"
}
