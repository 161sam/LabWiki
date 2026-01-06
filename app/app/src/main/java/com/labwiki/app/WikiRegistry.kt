package com.labwiki.app

data class WikiDef(
    val id: String,
    val name: String,
    val description: String,
    val remoteStartUrl: String,
    val remoteOfflineBase: String,
    val tags: List<String>
)

object WikiRegistry {
    val wikis: List<WikiDef> = listOf(
        WikiDef(
            id = "pentest-lab",
            name = "Pentest Lab",
            description = "Beispiel-Wiki mit Platzhalter-URLs.",
            remoteStartUrl = "https://example.com/pentest-lab/",
            remoteOfflineBase = "https://example.com/pentest-lab/offline",
            tags = listOf("Security", "Pentest")
        ),
        WikiDef(
            id = "networking",
            name = "Networking",
            description = "Netzwerk-Labs und Grundlagen.",
            remoteStartUrl = "https://example.com/networking/",
            remoteOfflineBase = "https://example.com/networking/offline",
            tags = listOf("Networking", "Fundamentals")
        ),
        WikiDef(
            id = "reverse-engineering",
            name = "Reverse Engineering",
            description = "Analysen, Tools und Workflows.",
            remoteStartUrl = "https://example.com/reverse-engineering/",
            remoteOfflineBase = "https://example.com/reverse-engineering/offline",
            tags = listOf("Security", "Reverse")
        ),
        WikiDef(
            id = "cloud-security",
            name = "Cloud Security",
            description = "Best Practices & Checklisten.",
            remoteStartUrl = "https://example.com/cloud-security/",
            remoteOfflineBase = "https://example.com/cloud-security/offline",
            tags = listOf("Cloud", "Security")
        ),
        WikiDef(
            id = "forensics",
            name = "Forensics",
            description = "Artefakte, Tools und Playbooks.",
            remoteStartUrl = "https://example.com/forensics/",
            remoteOfflineBase = "https://example.com/forensics/offline",
            tags = listOf("Forensics", "Investigation")
        )
    )

    fun getById(id: String): WikiDef? = wikis.firstOrNull { it.id == id }
}
