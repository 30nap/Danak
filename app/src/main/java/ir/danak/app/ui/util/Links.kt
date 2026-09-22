package ir.danak.app.ui.util

import java.net.URI

/**
 * Persian Wikipedia links are stored readable (…/wiki/خط_میخی), but not every browser that
 * may receive the intent accepts a raw non-ASCII URL. This percent-encodes the path as
 * UTF-8, leaving already-legal characters alone so English links are not double-encoded.
 */
fun browsableUrl(url: String): String {
    val parsed = URI(url)
    return URI(parsed.scheme, parsed.authority, parsed.path, parsed.query, parsed.fragment)
        .toASCIIString()
}
