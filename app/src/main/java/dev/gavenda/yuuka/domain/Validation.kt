package dev.gavenda.yuuka.domain

import java.net.URI

/*
 * Rules the API applies to a value, answered on the device as well because a save never waits for the
 * server to say no. Each is a plain predicate; what to say about a failure is the form's business, since
 * the same rule is worded differently for a category than for a tag.
 */

/** The longest a name may be; the API's own limit for an account, category, tag or account type. */
const val NAME_MAX = 80

/** The longest a payee may be. */
const val PAYEE_MAX = 120

/** The longest a transaction's notes may be. */
const val NOTES_MAX = 500

/** The most tags one transaction wears; the API refuses more. */
const val MAX_TAGS_PER_TRANSACTION = 10

private val CURRENCY_CODE = Regex("^[A-Za-z]{3}$")
private val HEX_COLOUR = Regex("^#[0-9a-fA-F]{6}$")

/** Exactly three letters, the shape of an ISO 4217 code; anything else is refused. */
fun isCurrencyCode(value: String): Boolean = CURRENCY_CODE.matches(value.trim())

/** A full `#rrggbb`; nothing shorter is accepted. */
fun isHexColour(value: String): Boolean = HEX_COLOUR.matches(value.trim())

/** Same name, whatever the case — what tags, and the payee history, are unique by. */
fun sameName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

/**
 * A logo link. It ends up in an image load, so only http(s) is accepted. An empty value is fine — the logo
 * is optional — which is why this is asked of a value that is not blank.
 */
fun isHttpUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    return (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) && !uri.host.isNullOrBlank()
}

/** The longest a logo link may be. */
const val LOGO_URL_MAX = 2048
