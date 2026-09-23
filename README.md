# DnsDiscord - In-App Custom DNS Plugin for Aliucord

A plugin for [Aliucord](https://aliucord.com/) (Discord for Android) that intercepts and routes Discord's network traffic through a custom DNS resolver **exclusively within the Discord app**.

---

## Features

- **In-App Scope Only:** Intercepts Discord's internal `OkHttpClient` and `Dns.SYSTEM` stack without touching system-wide Android DNS settings.
- **Multiple Protocols Supported:**
  - **DNS-over-HTTPS (DoH):** Encrypted DNS queries over HTTPS.
  - **Direct UDP (Port 53):** Standard DNS packet queries directly to custom DNS server IPs.
  - **Static Host Overrides:** Local `/etc/hosts`-style overrides directly in JSON (e.g. `gateway.discord.gg -> 162.159.135.232`).
- **Popular Presets Built-In:**
  - Cloudflare (`1.1.1.1`)
  - Google (`8.8.8.8`)
  - AdGuard DNS (`94.140.14.14` - ad and tracker blocking)
  - Quad9 (`9.9.9.9` - malware protection)
  - Custom Endpoints (user-defined URLs / IPs)
- **JSON Storage:** Complete configuration is stored as a formatted JSON document under `/sdcard/Aliucord/settings/DnsPlugin.json`. A raw JSON viewer/editor is accessible directly from the settings page.
- **Failover / Fallback:** Automatically falls back to standard system DNS if custom queries fail or time out (can be toggled).
- **In-App Diagnostics:** Built-in "Test DNS Resolution" button to test hostname resolution (e.g. `discord.com`) and measure response latency in milliseconds.

---

## Project Structure

```
dns-discord/
├── gradle/
│   └── libs.versions.toml
├── plugins/
│   ├── build.gradle.kts
│   └── DnsDiscord/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/aliucord/plugins/dns/
│           ├── DnsConfig.kt       # Configuration model & JSON serialization
│           ├── DnsResolver.kt     # OkHttp Dns implementation (DoH, UDP, Cache, Static)
│           ├── DnsPlugin.kt       # Aliucord plugin entrypoint & OkHttp method hooks
│           └── PluginSettings.kt  # Settings UI (Switches, Presets, Inputs, JSON Editor)
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## JSON Configuration Schema

Settings are saved in JSON format:

```json
{
  "enabled": true,
  "mode": "DOH",
  "preset": "CLOUDFLARE",
  "dohUrl": "https://cloudflare-dns.com/dns-query",
  "primaryDnsIp": "1.1.1.1",
  "secondaryDnsIp": "1.0.0.1",
  "fallbackToSystem": true,
  "staticHosts": {
    "gateway.discord.gg": "162.159.135.232",
    "cdn.discordapp.com": "162.159.130.233"
  }
}
```

---

## How to Build

1. Open the project directory `dns-discord/` in **Android Studio** (or build with Gradle on a machine with Android SDK and JDK 21+):
   ```bash
   ./gradlew :plugins:DnsDiscord:make
   ```
2. The built plugin `.zip` will be generated in `plugins/DnsDiscord/build/libs/`.
3. Copy the zip file into your device's `Aliucord/plugins/` directory, or run:
   ```bash
   ./gradlew :plugins:DnsDiscord:deployWithAdb
   ```


## Setting up GitHub

- Ensure that you have a remote branch named `builds`. This should have been automatically cloned
  if you ticked the "Include all branches" when creating a new repository using this template.
  If you did not do so, you can create an empty branch manually:
  ```shell
  $ git stash # To save any uncommitted changes, if applicable
  $ git checkout --orphan builds
  $ git rm -rf .
  $ git commit --allow-empty -m "feat: init builds"
  $ git push -u origin builds
  ```
- In order to accelerate builds, make sure that you properly enabled saving configuration cache
  in CI builds. To do this, you will have to generate an encryption key and add an actions repository
  secret. On a system with `openssl` installed, run:

  ```shell
  $ openssl rand -base64 16
  ```

  Take the output base64 string, and add a secret named `GRADLE_CACHE_ENCRYPTION_KEY` in
  your repository's Settings > Secrets and Variables > Actions > Repository Secrets.

## Publishing your plugin

1. Ensure you have a `builds` branch initialized in your Git repository.
    - If you checked "Include all branches" when cloning this template, then ignore.
2. Enable publishing to your own plugin repository's builds by setting the `deploy` property
   in your plugin's Gradle buildscript to true:

   `./plugins/MyFirstKotlinPlugin/build.gradle.kts`:
   ```kotlin
   aliucord {
       // ...
   
       // Excludes this plugin from publishing and global plugin repositories.
       // Set this to false if the plugin is unfinished
       deploy.set(true)
       
       // ...
   }
   ```
3. Create a Git commit with your changes, and push the `main` branch to Github. Ensure that the
   Github workflow that builds your plugins succeeds. Your plugins should have been deployed to the `builds`
   branch in your repository.
4. Join the [Aliucord Discord](https://discord.gg/EsNDvBaHVU), go to the `#plugin-development` channel,
   and request your plugin repository to be reviewed. If you need additional help in writing plugins,
   you are welcome to also ask any questions regarding development.
5. Once you have received approval, you will be granted permissions to post a plugin listing to the
   `#plugins-list` and `#new-plugins` channels. Additionally, create an GitHub issue to submit
   your plugin repository to internal listings by following the instructions
   [here](https://github.com/aliucord/plugins-repo#adding-your-own-plugins).

## Building from CLI

- On Linux & macOS, run `./gradlew :MyFirstKotlinPlugin:make` to build the plugin.
  Use `./gradlew MyFirstKotlinPlugin:deployWithAdb` to deploy directly to a connected device.
- On Windows, use `.\gradlew.bat :MyFirstKotlinPlugin:make` and `.\gradlew.bat MyFirstKotlinPlugin:deployWithAdb`
  for building and deploying, respectively.

The built plugin will be located at `$PLUGIN_DIR/builds/outputs/$PLUGIN.zip`. Note
that the `builds` directory is hidden from the tree view in Android Studio by default.

## License

This template ([Aliucord/plugins-template](https://github.com/Aliucord/plugins-template)) is hereby
released into the public domain. This license notice does not apply for repositories generated using
this template, and you must refer to the applicable downstream license.

We highly recommend adding the [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) license to your plugins!
To do so, copy the license text into a file named `LICENSE` at the repository root.

THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY
APPLICABLE LAW. EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT
HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM "AS IS" WITHOUT WARRANTY
OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM
IS WITH YOU. SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF
ALL NECESSARY SERVICING, REPAIR OR CORRECTION.
