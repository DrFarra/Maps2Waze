package com.maps2waze;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LinkParserTest {
    @Test
    public void keepsMapsAndWazeLinks() {
        assertEquals("https://maps.app.goo.gl/abc123",
                LinkParser.toUri("Mira aquí https://maps.app.goo.gl/abc123 nos vemos"));
        assertEquals("https://waze.com/ul?ll=19.4,-99.1&navigate=yes",
                LinkParser.toUri("https://waze.com/ul?ll=19.4,-99.1&navigate=yes"));
        assertEquals("https://www.google.com.mx/maps/place/x",
                LinkParser.toUri("https://www.google.com.mx/maps/place/x"));
    }

    @Test
    public void rejectsOtherLinks() {
        assertNull(LinkParser.toUri("https://evil.example/google.com"));
        assertNull(LinkParser.toUri("https://google.com.evil.example/"));
        assertNull(LinkParser.toUri("   "));
    }

    @Test
    public void plainAddressBecomesGeoSearch() {
        assertEquals("geo:0,0?q=Av.%20Reforma%20222%2C%20CDMX",
                LinkParser.toUri("Av. Reforma 222, CDMX"));
    }
}
