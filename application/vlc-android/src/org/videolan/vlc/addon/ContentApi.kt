package org.videolan.vlc.addon

/**
 * Element de contenu expose par l'API Xplayon.
 *
 * @property id identifiant du contenu
 * @property title titre affiche a l'ecran
 * @property position position de lecture sauvegardee, en secondes
 * @property uri adresse (http/https/...) du media a lire
 */
data class ContentItem(
    val id: String,
    val title: String,
    val position: Long,
    val uri: String
)

/**
 * Contrat de l'API de contenu Xplayon.
 *
 * Toutes les positions de lecture sont exprimees en **secondes**.
 */
interface ContentApi {
    /** URL de base de l'API ; utile pour le debogage. */
    val baseUrl: String

    /** Retourne la liste des contenus disponible. */
    suspend fun getItems(): List<ContentItem>

    /**
     * Retourne le contenu [id] avec sa position de lecture
     * sauvegardee (0 si aucune sauvegarde).
     */
    suspend fun getItem(id: String): ContentItem?

    /** Sauvegarde la position de lecture [positionSeconds] pour le contenu [id]. */
    suspend fun savePosition(id: String, positionSeconds: Long)
}