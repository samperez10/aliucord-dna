version = "1.0.0"
description = "Configure custom DNS (DoH, UDP, Static Hosts) exclusively within Discord with JSON storage and settings UI."

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Initial release: Custom DNS setup (DoH, UDP, and Static Host mappings)
        * In-app Settings UI with JSON configuration storage
        * Direct latency test button and fallback options
        """.trimIndent(),
    )
    deploy.set(false)
}
