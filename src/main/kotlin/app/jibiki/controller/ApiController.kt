package app.jibiki.controller

import app.jibiki.model.*
import app.jibiki.persistence.CachingDatabaseAccessor
import app.jibiki.spec.CreateUserSpec
import app.jibiki.spec.LoginSpec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.springframework.beans.BeanInstantiationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@CrossOrigin
@RestController
class ApiController(
        private val database: CachingDatabaseAccessor
) {
    @RequestMapping(method = [RequestMethod.GET], value = ["/sentences"], produces = ["application/json"])
    fun sentenceSearch(
            @RequestParam("query") query: String,
            @RequestParam("page", defaultValue = "0") page: Int,
            @RequestParam("minLength", defaultValue = "0") minLength: Int,
            @RequestParam("maxLength", defaultValue = "0") maxLength: Int
    ): Flux<SentenceBundle> {
        if (query.isEmpty())
            return Flux.empty()

        return database.getSentences(query, page)
                .filter { it.sentence.sentence.length >= minLength }
                .filter { if (maxLength != 0) it.sentence.sentence.length <= maxLength else true }
    }

    @RequestMapping(method = [RequestMethod.GET], value = ["/words"], produces = ["application/json"])
    fun wordSearch(
            @RequestParam("query") query: String,
            @RequestParam("page", defaultValue = "0") page: Int
    ): Flux<Word> {
        if (query.isEmpty())
            return Flux.empty()

        return database.getEntriesForWord(query, page)
                .flatMapSequential { database.getEntry(it) }
    }

    @RequestMapping(method = [RequestMethod.GET], value = ["/kanji"], produces = ["application/json"])
    fun kanjiSearch(
            @RequestParam("query") query: String
    ): Flux<Kanji> {
        if (query.isEmpty())
            return Flux.empty()

        return database.getKanji(query)
    }

    @RequestMapping(method = [RequestMethod.POST], value = ["/users/create"], consumes = ["application/x-www-form-urlencoded"])
    fun createUser(
            createUserSpec: CreateUserSpec
    ): Mono<ResponseEntity<HttpStatus>> {
        return database
                .createUser(createUserSpec)
                .map { ResponseEntity<HttpStatus>(it) }
    }

    @RequestMapping(method = [RequestMethod.POST], value = ["/users/login"], consumes = ["application/x-www-form-urlencoded"])
    fun loginUser(
            loginSpec: LoginSpec
    ): Mono<ResponseEntity<Void>> {
        return database
                .checkCredentials(loginSpec.email, loginSpec.password)
                .flatMap { database.getToken(it) }
                .map { ResponseEntity.noContent().header("Set-Cookie", "token=${it.token}; Expires=${it.expiry}; Max-Age=${it.expiry}; SameSite=Strict; HttpOnly").build<Void>() }
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
    }

    @RequestMapping(method = [RequestMethod.GET], value = ["/users/@me"], consumes = ["application/x-www-form-urlencoded"], produces = ["application/json"])
    fun getMe(
            @CookieValue("token") token: String
    ): Mono<User> {
        return database
                .checkToken(token)
                .switchIfEmpty(Mono.error(IllegalArgumentException("Invalid or expired token")))
                .flatMap {
                    database.getUser(Snowflake(it.snowflake!!))
                }
    }

    @ExceptionHandler(BeanInstantiationException::class)
    fun handleBeans(): ResponseEntity<String> {
        return ResponseEntity.badRequest().build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<String> {
        return ResponseEntity.badRequest().body("{\"message\": \"${exception.message}\"}")
    }
}