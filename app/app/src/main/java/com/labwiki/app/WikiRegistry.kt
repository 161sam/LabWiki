package com.labwiki.app

data class WikiDef(
    val id: String,
    val remoteStartUrl: String,
    val remoteOfflineBase: String
)

object WikiRegistry {
    val wikis: List<WikiDef> = listOf(
        WikiDef(
            id = "pentest-lab",
            remoteStartUrl = "https://example.com/pentest-lab/",
            remoteOfflineBase = "https://example.com/pentest-lab/offline"
        ),
        WikiDef(
            id = "networking",
            remoteStartUrl = "https://example.com/networking/",
            remoteOfflineBase = "https://example.com/networking/offline"
        ),
        WikiDef(
            id = "reverse-engineering",
            remoteStartUrl = "https://example.com/reverse-engineering/",
            remoteOfflineBase = "https://example.com/reverse-engineering/offline"
        ),
        WikiDef(
            id = "cloud-security",
            remoteStartUrl = "https://example.com/cloud-security/",
            remoteOfflineBase = "https://example.com/cloud-security/offline"
        ),
        WikiDef(
            id = "forensics",
            remoteStartUrl = "https://example.com/forensics/",
            remoteOfflineBase = "https://example.com/forensics/offline"
        )
    )

    fun getById(id: String): WikiDef? = wikis.firstOrNull { it.id == id }
}
