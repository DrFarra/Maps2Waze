package com.maps2waze;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns whatever was shared on the iPhone into a URI the head unit can open, or null to ignore it. */
final class LinkParser {
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern GOOGLE = Pattern.compile("(.+\\.)?google\\.((com|co)(\\.[a-z]{2})?|[a-z]{2})");

    static String toUri(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Matcher m = URL.matcher(text);
        if (m.find()) {
            String url = m.group();
            try {
                String host = new URI(url).getHost();
                // Only maps links: anyone who learns the topic must not be able to open arbitrary sites in the car.
                return host != null && isMapHost(host.toLowerCase()) ? url : null;
            } catch (Exception e) {
                return null;
            }
        }
        // Plain address text (e.g. pasted from WhatsApp): let the default maps app search it.
        try {
            return "geo:0,0?q=" + URLEncoder.encode(text.trim(), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static boolean isMapHost(String host) {
        return host.equals("goo.gl") || host.endsWith(".goo.gl")
                || host.equals("waze.com") || host.endsWith(".waze.com")
                || GOOGLE.matcher(host).matches();
    }
}
