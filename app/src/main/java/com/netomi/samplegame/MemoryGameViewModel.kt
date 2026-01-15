package com.netomi.samplegame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoryGameViewModel : ViewModel() {

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    private var matchedCardsCount = 0

    companion object {
        private const val CARD_PAIRS = 8
        private const val MISMATCH_DELAY = 500L
    }

    init {
        initializeGame()
    }

    fun initializeGame() {
        val cardValues = List(CARD_PAIRS) { it + 1 }
        val pairedCards = cardValues + cardValues
        val shuffledCards = pairedCards.shuffled().mapIndexed { index, value ->
            Card(id = index, value = value)
        }
        matchedCardsCount = 0
        _gameState.value = GameState(cards = shuffledCards, moves = 0, isGameComplete = false, canFlipCard = true)
    }

    fun onCardClick(cardId: Int) {
        val currentState = _gameState.value
        
        // Ignore taps if we can't flip cards (during mismatch delay)
        if (!currentState.canFlipCard) return
        
        val clickedCard = currentState.cards.find { it.id == cardId } ?: return
        
        // Ignore taps on already flipped or matched cards
        if (clickedCard.isFlipped || clickedCard.isMatched) return
        
        // Get currently flipped unmatched cards
        val flippedCards = currentState.cards.filter { it.isFlipped && !it.isMatched }
        
        when (flippedCards.size) {
            0 -> {
                // No cards flipped - flip this one
                flipCard(cardId)
            }
            1 -> {
                // One card already flipped - flip second and check for match
                val firstCard = flippedCards[0]
                flipCard(cardId)
                
                // Increment moves (a move is a pair attempt)
                val newMoves = currentState.moves + 1
                _gameState.value = _gameState.value.copy(moves = newMoves)
                
                // Check if cards match
                if (firstCard.value == clickedCard.value) {
                    // Match found - mark both as matched
                    markCardsAsMatched(firstCard.id, cardId)
                } else {
                    // No match - flip both back after delay
                    viewModelScope.launch {
                        // Disable further flips during delay
                        _gameState.value = _gameState.value.copy(canFlipCard = false)
                        delay(MISMATCH_DELAY)
                        flipCardsDown(firstCard.id, cardId)
                        _gameState.value = _gameState.value.copy(canFlipCard = true)
                    }
                }
            }
            else -> {
                // Should not happen, but ignore if more than one card is flipped
                return
            }
        }
    }

    private fun flipCard(cardId: Int) {
        val updatedCards = _gameState.value.cards.map { card ->
            if (card.id == cardId) card.copy(isFlipped = true) else card
        }
        _gameState.value = _gameState.value.copy(cards = updatedCards)
    }

    private fun flipCardsDown(cardId1: Int, cardId2: Int) {
        val updatedCards = _gameState.value.cards.map { card ->
            if (card.id == cardId1 || card.id == cardId2) {
                card.copy(isFlipped = false)
            } else {
                card
            }
        }
        _gameState.value = _gameState.value.copy(cards = updatedCards)
    }

    private fun markCardsAsMatched(cardId1: Int, cardId2: Int) {
        val updatedCards = _gameState.value.cards.map { card ->
            if (card.id == cardId1 || card.id == cardId2) {
                card.copy(isMatched = true)
            } else {
                card
            }
        }
        
        // Increment matched count by 2 (we just matched a pair)
        matchedCardsCount += 2
        val isComplete = matchedCardsCount == updatedCards.size
        
        _gameState.value = _gameState.value.copy(
            cards = updatedCards,
            isGameComplete = isComplete
        )
    }

    fun resetGame() {
        initializeGame()
    }
}
