package org.llm.com.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.llm.com.exception.ChatbotException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects URLs inside user messages and fetches the page content so the
 * LLM can analyse them inline.
 *
 * <h3>Security (InfoSec) — SSRF Prevention</h3>
 * <p>Server-Side Request Forgery (SSRF) is a critical risk when a server
 * fetches user-supplied URLs.  This service applies a multi-layer defence:
 *
 * <ol>
 *   <li><b>DNS resolution before connect</b>: The hostname is resolved via
 *       {@link InetAddress#getByName(String)} <em>before</em> Jsoup opens any
 *       TCP connection.  If the resolved IP is loopback, site-local (RFC-1918),
 *       link-local, any-local, or multicast it is rejected immediately.</li>
 *   <li><b>Scheme whitelist</b>: Only {@code http://} and {@code https://}
 *       schemes are accepted.  {@code file://}, {@code ftp://}, etc. are
 *       rejected.</li>
 *   <li><b>Timeout</b>: Connect/read timeout (default 10 s) prevents
 *       slow-loris / hanging connections.</li>
 *   <li><b>Content cap</b>: Extracted text is truncated at
 *       {@code max-content-chars} (default 6 000) to bound LLM token cost and
 *       protect against infinite-response attacks.</li>
 * </ol>
 *
 * <h3>Architecture</h3>
 * <p>URL detection uses a simple regex.  Fetching uses Jsoup which strips
 * all HTML tags; {@code <script>}, {@code <style>}, {@code <nav>},
 * {@code <footer>}, and {@code <aside>} nodes are removed before text
 * extraction to reduce noise.
 */
@Slf4j
@Service
public class UrlFetchService {

    // Matches http(s) URLs; captures the full URL including query + fragment
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${chatbot.url-fetch.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${chatbot.url-fetch.max-content-chars:6000}")
    private int maxContentChars;

    @Value("${chatbot.url-fetch.enabled:true}")
    private boolean enabled;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns every URL found in {@code text}.
     */
    public List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isBlank()) return urls;
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            urls.add(m.group());
        }
        return urls;
    }

    /**
     * Fetches and returns the readable text of a web page.
     *
     * @param urlStr the URL to fetch (must be http/https)
     * @return extracted text, truncated at {@code maxContentChars},
     *         or empty if the fetch fails gracefully
     * @throws ChatbotException with code {@code SSRF_BLOCKED} when the URL
     *         resolves to a private / reserved address
     */
    public Optional<String> fetchContent(String urlStr) {
        if (!enabled) {
            log.debug("URL fetching is disabled — skipping [{}]", urlStr);
            return Optional.empty();
        }

        try {
            URI uri = URI.create(urlStr);

            // [InfoSec] Scheme whitelist — block file://, ftp://, etc.
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                log.warn("[InfoSec] Blocked non-HTTP/S URL scheme=[{}]", scheme);
                throw new ChatbotException("INVALID_URL_SCHEME",
                        "Only http:// and https:// URLs are supported.");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }

            // [InfoSec] SSRF: resolve DNS first, block private/reserved IPs
            InetAddress resolved = InetAddress.getByName(host);
            if (isPrivateOrReserved(resolved)) {
                log.warn("[InfoSec] SSRF attempt blocked — URL=[{}] resolved to reserved IP=[{}]",
                        urlStr, resolved.getHostAddress());
                throw new ChatbotException("SSRF_BLOCKED",
                        "The URL targets an internal/private network address and cannot be fetched " +
                        "for security reasons.");
            }

            log.info("Fetching URL=[{}] resolvedIP=[{}]", urlStr, resolved.getHostAddress());

            Document doc = Jsoup.connect(urlStr)
                    .timeout(timeoutSeconds * 1_000)
                    .followRedirects(true)
                    .maxBodySize(2 * 1024 * 1024) // 2 MB max download
                    .userAgent("Mozilla/5.0 (compatible; ChatbotAnalyser/1.0)")
                    .get();

            // Strip noisy structural elements before text extraction
            doc.select("script, style, nav, footer, header, aside, form, iframe").remove();

            String title   = doc.title();
            String body    = doc.body().text();
            String content = (title.isBlank() ? "" : "Title: " + title + "\n\n") + body;

            if (content.length() > maxContentChars) {
                content = content.substring(0, maxContentChars) + "\n... [content truncated]";
            }

            log.info("URL fetch success: url=[{}] contentChars=[{}]", urlStr, content.length());
            return Optional.of(content);

        } catch (ChatbotException e) {
            throw e; // SSRF / scheme errors — propagate to the caller
        } catch (Exception e) {
            log.warn("URL fetch failed for [{}]: {}", urlStr, e.getMessage());
            return Optional.empty(); // connectivity / timeout failures are non-fatal
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} for loopback, site-local (RFC-1918/RFC-4193),
     * link-local, any-local, and multicast addresses.
     *
     * <p>[InfoSec] This covers:
     * 10.x.x.x, 172.16-31.x.x, 192.168.x.x (site-local),
     * 127.x.x.x / ::1 (loopback),
     * 169.254.x.x / fe80:: (link-local),
     * 0.0.0.0 / :: (any-local),
     * 224-239.x.x.x (multicast / metadata endpoints like 169.254.169.254).
     */
    private boolean isPrivateOrReserved(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress();
    }
}

