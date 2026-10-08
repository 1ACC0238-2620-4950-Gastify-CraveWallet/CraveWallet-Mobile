package com.cravewallet.app.data

import androidx.compose.ui.graphics.Color

/** Servicio conocido: sirve para el paso 1 del alta y para pintar avatares con el color de la marca. */
data class KnownService(
    val name: String,
    val initials: String,
    val brand: Color,
    val onBrand: Color,
    val category: Category,
    val amount: Double,
    val currency: Currency,
    val popular: Boolean = false,
)

object Catalog {
    val services = listOf(
        KnownService("Netflix", "N", Color(0xFFE50914), Color.White, Category.STREAMING, 44.90, Currency.PEN, popular = true),
        KnownService("Spotify Premium", "S", Color(0xFF1DB954), Color(0xFF0F172A), Category.MUSICA, 20.90, Currency.PEN, popular = true),
        KnownService("Disney+", "D+", Color(0xFF113CCF), Color.White, Category.STREAMING, 38.90, Currency.PEN, popular = true),
        KnownService("Max", "M", Color(0xFF002BE7), Color.White, Category.STREAMING, 34.90, Currency.PEN, popular = true),
        KnownService("Smart Fit Black", "SF", Color(0xFFFFC20E), Color(0xFF0F172A), Category.FITNESS, 129.90, Currency.PEN, popular = true),
        KnownService("Google One", "G", Color(0xFF4285F4), Color.White, Category.PRODUCTIVIDAD, 7.90, Currency.PEN, popular = true),
        KnownService("ChatGPT Plus", "AI", Color(0xFF10A37F), Color.White, Category.PRODUCTIVIDAD, 20.00, Currency.USD, popular = true),
        KnownService("Rappi Prime", "R", Color(0xFFFF441F), Color.White, Category.DELIVERY, 25.90, Currency.PEN, popular = true),
        KnownService("Crunchyroll", "C", Color(0xFFF47521), Color(0xFF0F172A), Category.STREAMING, 7.99, Currency.USD, popular = true),
        KnownService("LinkedIn Premium", "in", Color(0xFF0A66C2), Color.White, Category.PRODUCTIVIDAD, 39.99, Currency.USD),
        KnownService("YouTube Premium", "YT", Color(0xFFFF0000), Color.White, Category.STREAMING, 25.90, Currency.PEN),
        KnownService("Amazon Prime", "a", Color(0xFF00A8E1), Color(0xFF0F172A), Category.STREAMING, 14.99, Currency.USD),
        KnownService("PedidosYa Plus", "P", Color(0xFFFA0050), Color.White, Category.DELIVERY, 14.90, Currency.PEN),
        KnownService("Notion Plus", "N", Color(0xFF0F172A), Color.White, Category.PRODUCTIVIDAD, 12.00, Currency.USD),
        KnownService("Apple Music", "A", Color(0xFFFA2D48), Color.White, Category.MUSICA, 16.90, Currency.PEN),
        KnownService("Canva Pro", "C", Color(0xFF7D2AE8), Color.White, Category.PRODUCTIVIDAD, 25.00, Currency.PEN),
        KnownService("Microsoft 365", "M", Color(0xFFD83B01), Color.White, Category.PRODUCTIVIDAD, 29.00, Currency.PEN),
        KnownService("Duolingo Super", "D", Color(0xFF58CC02), Color(0xFF0F172A), Category.OTROS, 24.90, Currency.PEN),
        KnownService("Bodytech", "B", Color(0xFF1F2937), Color.White, Category.FITNESS, 189.00, Currency.PEN),
        KnownService("iCloud+", "i", Color(0xFF3693F3), Color.White, Category.OTROS, 3.90, Currency.PEN),
    )

    val popular: List<KnownService> get() = services.filter { it.popular }

    fun find(name: String): KnownService? =
        services.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun search(query: String): List<KnownService> {
        val q = query.trim()
        if (q.isEmpty()) return popular
        return services.filter { it.name.contains(q, ignoreCase = true) || it.category.label.contains(q, ignoreCase = true) }
    }
}
