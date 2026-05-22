package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;

public class WebFetchTool {

    private static final Logger LOG = LoggerFactory.getLogger(WebFetchTool.class);
    private static final int MAX_CONTENT = 15000;
    private static final long MAX_RESPONSE_SIZE = 5 * 1024 * 1024; // 5MB

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Tool(name = "web_fetch", description = "Fetch and read the content of a web page. Returns the text content of the page. Useful for reading documentation, articles, API references, etc.")
    public String webFetch(
        @ToolParam(description = "URL of the web page to fetch") String url
    ) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null) {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return "Error: Access to internal/private addresses is blocked";
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json")
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, new HttpResponse.BodyHandler<String>() {
                @Override
                public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
                    return new SizeLimitSubscriber(MAX_RESPONSE_SIZE);
                }
            });

            if (response.statusCode() != 200) {
                return "Error: HTTP " + response.statusCode() + " for URL: " + url;
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            String body = response.body();

            // If it's JSON, return as-is
            if (contentType.contains("json")) {
                return truncate(body, MAX_CONTENT);
            }

            // If it's plain text, return as-is
            if (contentType.contains("text/plain")) {
                return truncate(body, MAX_CONTENT);
            }

            // Otherwise, extract text from HTML
            String text = extractText(body);
            return truncate(text, MAX_CONTENT);

        } catch (Exception e) {
            LOG.error("Failed to fetch URL: {}", url, e);
            return "Error fetching URL: " + e.getMessage();
        }
    }

    private String extractText(String html) {
        // Remove script and style tags
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", "");

        // Remove HTML comments
        text = text.replaceAll("(?s)<!--.*?-->", "");

        // Extract title
        String title = "";
        java.util.regex.Matcher titleMatcher = java.util.regex.Pattern.compile(
            "(?i)<title[^>]*>(.*?)</title>", java.util.regex.Pattern.DOTALL
        ).matcher(text);
        if (titleMatcher.find()) {
            title = titleMatcher.group(1).trim();
        }

        // Convert common elements to text
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)<h([1-6])[^>]*>", "\n\n");
        text = text.replaceAll("(?i)</h[1-6]>", "\n");

        // Extract link hrefs
        text = text.replaceAll("(?i)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2 ($1)");

        // Extract image alt text
        text = text.replaceAll("(?i)<img[^>]*alt=\"([^\"]+)\"[^>]*>", "[$1]");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Decode HTML entities
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("&#x27;", "'");
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&#\\d+;", "");

        // Clean up whitespace
        text = text.replaceAll("[ \t]+", " ");
        text = text.replaceAll("\n{3,}", "\n\n");
        text = text.trim();

        if (!title.isEmpty()) {
            return "Title: " + title + "\n\n" + text;
        }
        return text;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n\n... (content truncated, " + text.length() + " chars total)";
    }

    private static class SizeLimitSubscriber implements HttpResponse.BodySubscriber<String> {
        private final long maxSize;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long received = 0;
        private volatile boolean exceeded = false;
        private final java.util.concurrent.CompletableFuture<String> result = new java.util.concurrent.CompletableFuture<>();

        SizeLimitSubscriber(long maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer bb : items) {
                int remaining = bb.remaining();
                if (received + remaining > maxSize) {
                    exceeded = true;
                    buffer.reset();
                    result.complete("Error: Response too large (exceeded " + maxSize + " bytes)");
                    return;
                }
                byte[] bytes = new byte[remaining];
                bb.get(bytes);
                buffer.writeBytes(bytes);
                received += remaining;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (!exceeded) {
                result.complete(buffer.toString());
            }
        }

        @Override
        public java.util.concurrent.CompletionStage<String> getBody() {
            return result;
        }
    }
}
