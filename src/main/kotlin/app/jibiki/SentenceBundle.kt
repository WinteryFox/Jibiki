package app.jibiki

import com.fasterxml.jackson.annotation.JsonUnwrapped

data class SentenceBundle(
        @JsonUnwrapped
        val sentence: Sentence,
        val translations: List<Sentence>
)