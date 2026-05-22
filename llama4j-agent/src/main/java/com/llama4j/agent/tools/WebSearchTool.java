package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSearchTool {

    private static final Logger LOG = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_RESULTS = 8;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Tool(name = "web_search", description = "Search the web for information. Returns search results with titles, URLs, and snippets. Use this to find up-to-date information, documentation, answers to technical questions, etc.")
    public String webSearch(
        @ToolParam(description = "Search query") String query,
        @ToolParam(description = "Number of results to return (default 5, max 8)", type = "integer", required = false) int count
    ) {
        try {
            int limit = (count > 0 && count <= MAX_RESULTS) ? count : 5;
            List<SearchResult> results = searchDuckDuckGo(query, limit);

            if (results.isEmpty()) {
                return "No search results found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append("\n\n");

            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(String.format("[%d] %s\n", i + 1, r.title));
                sb.append("    URL: ").append(r.url).append("\n");
                sb.append("    ").append(r.snippet).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            LOG.error("Web search failed for query: {}", query, e);
            return "Search error: " + e.getMessage();
        }
    }

    private List<SearchResult> searchDuckDuckGo(String query, int limit) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String html = response.body();

        return parseDuckDuckGoResults(html, limit);
    }

    private List<SearchResult> parseDuckDuckGoResults(String html, int limit) {
        List<SearchResult> results = new ArrayList<>();

        // DuckDuckGo HTML result pattern
        Pattern resultPattern = Pattern.compile(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?" +
            "<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.DOTALL
        );

        // Alternative pattern for different DDG layouts
        Pattern altPattern = Pattern.compile(
            "<a[^>]*class=\"result-link\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?" +
            "<span[^>]*class=\"result__snippet\"[^>]*>(.*?)</span>",
            Pattern.DOTALL
        );

        Matcher matcher = resultPattern.matcher(html);
        if (!matcher.find()) {
            matcher = altPattern.matcher(html);
        }

        while (matcher.find() && results.size() < limit) {
            String resultUrl = extractDdgUrl(matcher.group(1));
            String title = cleanHtml(matcher.group(2));
            String snippet = cleanHtml(matcher.group(3));

            if (!title.isEmpty() && !resultUrl.isEmpty()) {
                results.add(new SearchResult(title, resultUrl, snippet));
            }
        }

        // Fallback: try simpler pattern
        if (results.isEmpty()) {
            Pattern simplePattern = Pattern.compile(
                "href=\"(https?://[^\"]+)\"[^>]*>([^<]{10,80})</a>",
                Pattern.DOTALL
            );
            Matcher simpleMatcher = simplePattern.matcher(html);
            while (simpleMatcher.find() && results.size() < limit) {
                String resultUrl = simpleMatcher.group(1);
                String title = cleanHtml(simpleMatcher.group(2));
                if (!title.isEmpty() && !resultUrl.contains("duckduckgo.com")) {
                    results.add(new SearchResult(title, resultUrl, ""));
                }
            }
        }

        return results;
    }

    private String extractDdgUrl(String href) {
        // DDG wraps URLs in a redirect; extract the actual URL
        if (href.contains("uddg=")) {
            int idx = href.indexOf("uddg=");
            if (idx >= 0) {
                String encoded = href.substring(idx + 5);
                int end = encoded.indexOf('&');
                if (end > 0) encoded = encoded.substring(0, end);
                try {
                    return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return encoded;
                }
            }
        }
        return href;
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#x27;", "'")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private record SearchResult(String title, String url, String snippet) {}
}
