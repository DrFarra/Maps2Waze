package com.maps2waze;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns whatever was shared on the iPhone into a URI the head unit can open, or null to ignore it. */
final class LinkParser {
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern GOOGLE = Pattern.compile("(.+\\.)?google\\.((com|co)(\\.[a-z]{2})?|[a-z]{2})");
    private static final String NUM = "(-?\\d{1,3}\\.\\d+)";
    private static final Pattern COORDS = Pattern.compile(NUM + ",\\s*" + NUM);
    private static final Pattern PIN = Pattern.compile("!3d" + NUM + "!4d" + NUM);
    private static final Pattern AT = Pattern.compile("@" + NUM + "," + NUM);
    private static final Pattern PREVIEW = Pattern.compile("href=\"(/maps/preview/place\\?[^\"]+)\"");
    private static final Pattern PLACE_LATLNG = Pattern.compile("\\[null,null," + NUM + "," + NUM + "\\]");
    private static final Pattern WAZE_UL = Pattern.compile("https://(?:www\\.)?waze\\.com/ul\\?(.+)");
    private static final Pattern PATH = Pattern.compile("/maps/(?:place|search|dir)/(.+?)(?:/@|/data=|\\?|$)");

    static String toUri(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Matcher m = URL.matcher(text);
        if (m.find()) {
            String url = m.group();
            String host = host(url);
            // Only maps links: anyone who learns the topic must not be able to open arbitrary sites in the car.
            return host != null && isMapHost(host) ? url : null;
        }
        // Plain address text (e.g. pasted from WhatsApp): search it in Waze.
        return wazeQuery(text.trim());
    }

    /** Short links (maps.app.goo.gl, goo.gl/maps) must be expanded over the network before toWaze can read them. */
    static boolean isShortLink(String url) {
        String host = host(url);
        return host != null && (host.equals("goo.gl") || host.endsWith(".goo.gl"));
    }

    static boolean isGoogleLink(String url) {
        String host = host(url);
        return isShortLink(url) || host != null && GOOGLE.matcher(host).matches();
    }

    /** Waze link for the destination in a full Google Maps URL, or null if none can be found. */
    static String toWaze(String url) {
        String host = host(url);
        if (host == null || !GOOGLE.matcher(host).matches()) return null;
        Matcher m = PIN.matcher(url);
        String lat = null, lng = null;
        while (m.find()) { // last pin is the destination
            lat = m.group(1);
            lng = m.group(2);
        }
        if (lat != null) return wazeCoords(lat, lng);

        for (String name : new String[]{"destination", "daddr", "q", "query", "ll"}) {
            m = Pattern.compile("[?&]" + name + "=([^&#]+)").matcher(url);
            if (m.find()) return wazeText(decode(m.group(1)));
        }
        m = PATH.matcher(url);
        if (m.find()) {
            String p = m.group(1);
            String dest = p.substring(p.lastIndexOf('/') + 1); // dir/Origin/Destination -> Destination
            if (!dest.isEmpty()) return wazeText(decode(dest));
        }
        m = AT.matcher(url); // map center: least precise, last resort
        return m.find() ? wazeCoords(m.group(1), m.group(2)) : null;
    }

    /** Path of Google's place-details request embedded in a maps page, or null. */
    static String previewPath(String html) {
        Matcher m = PREVIEW.matcher(html);
        return m.find() ? m.group(1).replace("&amp;", "&") : null;
    }

    /** Waze link to the exact pin in Google's place-details data (first [null,null,lat,lng] is the place), or null. */
    static String wazeFromPreview(String data) {
        Matcher m = PLACE_LATLNG.matcher(data);
        return m.find() ? wazeCoords(m.group(1), m.group(2)) : null;
    }

    /** waze:// form of a waze.com/ul?... link (Waze reads its own scheme even when it ignores web links), or null. */
    static String wazeScheme(String url) {
        Matcher m = WAZE_UL.matcher(url);
        return m.matches() ? "waze://?" + m.group(1) : null;
    }

    static boolean isWazeSearch(String wazeUrl) {
        return wazeUrl.startsWith("https://waze.com/ul?q=");
    }

    private static String wazeText(String text) {
        Matcher c = COORDS.matcher(text.trim());
        return c.matches() ? wazeCoords(c.group(1), c.group(2)) : wazeQuery(text.trim());
    }

    private static String wazeCoords(String lat, String lng) {
        return "https://waze.com/ul?ll=" + lat + "," + lng + "&navigate=yes";
    }

    private static String wazeQuery(String text) {
        try {
            return "https://waze.com/ul?q=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20") + "&navigate=yes";
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String host(String url) {
        try {
            String h = new URI(url).getHost();
            return h == null ? null : h.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isMapHost(String host) {
        return host.equals("goo.gl") || host.endsWith(".goo.gl")
                || host.equals("waze.com") || host.endsWith(".waze.com")
                || GOOGLE.matcher(host).matches();
    }
}
