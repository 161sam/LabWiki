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
    private fun offlineBase(url: String): String = url.trimEnd('/')

    val wikis: List<WikiDef> = listOf(
        WikiDef(
            id = "pentest-lab-blue-red-team-training",
            name = "Pentest Lab: Blue/Red Team Training",
            description = "Trainingspfade für Blue- und Red-Team-Übungen.",
            remoteStartUrl = "https://161sam.github.io/pentest-lab-blue-red-team-training/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/pentest-lab-blue-red-team-training/offline"),
            tags = listOf("Pentest", "Blue Team", "Red Team")
        ),
        WikiDef(
            id = "smolotchi",
            name = "Smolotchi",
            description = "Kompakte Labs und Cheatsheets für Pentest-Themen.",
            remoteStartUrl = "https://161sam.github.io/smolotchi/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/smolotchi/offline"),
            tags = listOf("Pentest", "Cheatsheet")
        ),
        WikiDef(
            id = "forensic-playbook",
            name = "Forensic Playbook",
            description = "Playbooks und Prozesse für Forensik-Aufgaben.",
            remoteStartUrl = "https://161sam.github.io/Forensic-Playbook/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/Forensic-Playbook/offline"),
            tags = listOf("Forensics", "Incident Response")
        ),
        WikiDef(
            id = "pentest-home-lab-sbcs",
            name = "Pentest Home Lab SBCs",
            description = "Pentest-Home-Lab-Projekte für Single-Board-Computer.",
            remoteStartUrl = "https://161sam.github.io/Pentest-Home-Lab-SBCs/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/Pentest-Home-Lab-SBCs/offline"),
            tags = listOf("Pentest", "Home Lab", "SBC")
        ),
        WikiDef(
            id = "pentest-lab-shannon",
            name = "Pentest Lab Shannon",
            description = "Labs und Notizen für das Shannon-Pentest-Lab.",
            remoteStartUrl = "https://161sam.github.io/Pentest-Lab-shannon/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/Pentest-Lab-shannon/offline"),
            tags = listOf("Pentest", "Lab")
        ),
        WikiDef(
            id = "pentest-lab-cai",
            name = "Pentest Lab CAI",
            description = "Pentest-Lab-Inhalte rund um CAI.",
            remoteStartUrl = "https://161sam.github.io/Pentest-Lab-CAI/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/Pentest-Lab-CAI/offline"),
            tags = listOf("Pentest", "Lab")
        ),
        WikiDef(
            id = "pentest-lab-pentestgpt",
            name = "Pentest Lab PentestGPT",
            description = "Guides und Übungen zum PentestGPT-Lab.",
            remoteStartUrl = "https://161sam.github.io/Pentest-Lab-PentestGPT/",
            remoteOfflineBase = offlineBase("https://161sam.github.io/Pentest-Lab-PentestGPT/offline"),
            tags = listOf("Pentest", "AI")
        )
    )

    fun getById(id: String): WikiDef? = wikis.firstOrNull { it.id == id }
}
