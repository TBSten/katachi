package me.tbsten.katachi.test.dokka

/**
 * Inline sources for the llms specs, in the format of Dokka's `testInline`.
 *
 * A small library with what the llms files have to carry: module and package docs, a class with
 * a constructor and members, KDoc block tags, a code block, headings inside a KDoc, a link to
 * another declaration, an overload, a deprecated function, and a package the default `optionalPackagePatterns` send to
 * `Optional`.
 */
internal object LlmsSources {
    /** The `includes` file of [SHOP], relative to the inline sources. */
    const val INCLUDES: String = "src/main/kotlin/docs.md"

    val SHOP: String = """
        |/src/main/kotlin/docs.md
        |# Module shop
        |
        |Everything a shop needs.
        |
        |The second paragraph is not part of the summary.
        |
        |# Package shop.api
        |
        |The entry points of the shop.
        |
        |/src/main/kotlin/shop/api/Cart.kt
        |package shop.api
        |
        |/**
        | * A cart of [Item]s, checked out with [checkout].
        | *
        | * ```kotlin
        | * val cart = Cart("alice")
        | * ```
        | *
        | * @featured Start here to build an order.
        | * @property owner Who the cart belongs to.
        | */
        |public class Cart(public val owner: String) {
        |    /**
        |     * Adds [item] to the cart.
        |     *
        |     * @param item What to add.
        |     * @return The cart itself, for chaining.
        |     * @throws IllegalStateException When the cart is already checked out.
        |     */
        |    public fun add(item: Item): Cart = this
        |}
        |
        |/** One thing to buy. */
        |public data class Item(public val name: String)
        |
        |/**
        | * Pays for [cart].
        | *
        | * @featured
        | */
        |public fun checkout(cart: Cart): Receipt = Receipt
        |
        |/** An overload on the same page as the one above, with a summary of its own. */
        |public fun checkout(cart: Cart, coupon: String): Receipt = Receipt
        |
        |/** Pays for nothing. */
        |@Deprecated("Use checkout")
        |public fun pay(): Unit = Unit
        |
        |/** The currency every price is in. */
        |public val currency: String = "EUR"
        |
        |/**
        | * What a checkout returns.
        | *
        | * ## Example 1: keeping the receipt
        | *
        | * Store it with the order.
        | *
        | * ### Details
        | *
        | * Nothing else to it.
        | */
        |public object Receipt
        |
        |/src/main/kotlin/shop/internal/Ledger.kt
        |package shop.internal
        |
        |/** Bookkeeping that only the shop itself calls. */
        |public class Ledger
    """.trimMargin()
}
