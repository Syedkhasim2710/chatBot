package org.llm.com.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches real-time weather from <a href="https://wttr.in">wttr.in</a>.
 *
 * <p>wttr.in is completely free, requires no API key, and returns
 * human-readable weather summaries.  When a user asks about weather
 * this data is prepended to the prompt so the LLM answers with real facts
 * instead of declining or speculating.
 *
 * <p>Query detection covers phrases like:
 * <ul>
 *   <li>"what's the weather in Bangalore"</li>
 *   <li>"weather at London today"</li>
 *   <li>"temperature in Tokyo"</li>
 *   <li>"how's the weather in New York"</li>
 * </ul>
 */
@Slf4j
@Service
public class WeatherService {

    // Matches: "weather in X", "temperature in X", "how's the weather in X", etc.
    private static final Pattern WEATHER_CITY_PATTERN = Pattern.compile(
            "(?i)(?:weather|temperature|temp|climate|forecast|raining|sunny|cold|hot)" +
            "\\s+(?:in|at|for|of|near)\\s+" +
            "([\\w\\s]{2,40}?)(?:\\?|$|,|\\.|\\s+today|\\s+now|\\s+currently|\\s+right now)",
            Pattern.CASE_INSENSITIVE
    );

    private final RestClient restClient;

    public WeatherService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://wttr.in")
                .defaultHeader("User-Agent", "chatbot/1.0 (curl)")
                .defaultHeader("Accept", "text/plain")
                .build();
    }

    /**
     * Returns a one-line weather summary for {@code city}, or empty if not a
     * weather query or if the fetch fails.
     *
     * <p>Format example:
     * {@code Bangalore, Karnataka, India: ⛅️ Partly cloudy, 🌡️+29°C (feels +32°C), Humidity: 72%, Wind: ↙13km/h}
     */
    public Optional<String> fetchIfWeatherQuery(String userMessage) {
        String city = detectCity(userMessage);
        if (city == null) return Optional.empty();

        log.debug("Weather query detected — fetching data for city=[{}]", city);
        try {
            String encoded = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
            // wttr.in format tokens:
            //   %l = location   %C = condition text   %t = temperature
            //   %f = feels-like %h = humidity         %w = wind
            String format = "%l: %C, Temperature: %t (feels like %f), Humidity: %h, Wind: %w";
            String encodedFormat = URLEncoder.encode(format, StandardCharsets.UTF_8);
            String weather = restClient.get()
                    .uri("/" + encoded + "?format=" + encodedFormat)
                    .retrieve()
                    .body(String.class);

            if (weather != null && !weather.isBlank()) {
                log.info("Weather fetched for city=[{}]: {}", city, weather.trim());
                return Optional.of(weather.trim());
            }
        } catch (Exception ex) {
            log.warn("Weather fetch failed for city=[{}]: {}", city, ex.getMessage());
        }
        return Optional.empty();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    String detectCity(String message) {
        Matcher m = WEATHER_CITY_PATTERN.matcher(message);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}

