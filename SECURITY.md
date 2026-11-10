# Security policy

## Supported versions

Security fixes go into the latest release only. Please update before you report a problem.

| Version | Supported |
|---|---|
| 1.9.x (latest) | ✅ |
| older | ❌ |

## Reporting a vulnerability

**Please do not open a public issue.** Report it privately through GitHub instead:
[Security → Report a vulnerability](https://github.com/olex-labs/p-layer/security/advisories/new).

Please include:
- what an attacker could do, for example read files, crash the app or run code;
- the steps or a sample file that reproduces it;
- the P-layer and Android versions.

You will get a reply within 7 days. Once a fix is released, the advisory is published and you are credited, unless you would rather stay anonymous.

## Scope

P-layer reads local audio files and their tags, and makes HTTPS requests to LRCLIB only when online lyrics are on. The most relevant areas are:
- the parsers for tags and lyrics: ID3, FLAC Vorbis comments and LRC (`app/.../lyrics`);
- playlist import (M3U);
- the components other apps can reach: the media session and the media button receiver.
