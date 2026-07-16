package com.cartonianimati.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContentItem(
    val title: String = "",
    val url: String = "",
    val image: String = "",
    val category: String = "",
    val categorySlug: String = "",
    val synopsis: String = "",
    val originalTitle: String = "",
    val country: String = "",
    val year: String = "",
    val status: String = "",
    val totalEpisodes: String = "",
    val availableEpisodes: String = "",
    val episodes: List<Episode> = emptyList()
) : Parcelable

@Parcelize
data class PlayerLink(
    val label: String = "",
    val url: String = ""
) : Parcelable

@Parcelize
data class Episode(
    val title: String = "",
    val url: String = "",
    val season: Int = 0,
    val number: Int = 0,
    val players: List<PlayerLink> = emptyList()
) : Parcelable

data class CategorySection(
    val title: String,
    val items: List<ContentItem>
)
