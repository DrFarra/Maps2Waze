package com.maps2waze;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void plainAddressBecomesWazeSearch() {
        assertEquals("https://waze.com/ul?q=Av.%20Reforma%20222%2C%20CDMX&navigate=yes",
                LinkParser.toUri("Av. Reforma 222, CDMX"));
    }

    @Test
    public void shortLinks() {
        assertTrue(LinkParser.isShortLink("https://maps.app.goo.gl/abc"));
        assertTrue(LinkParser.isShortLink("https://goo.gl/maps/abc"));
        assertFalse(LinkParser.isShortLink("https://www.google.com/maps/place/x"));
    }

    @Test
    public void googleMapsToWaze() {
        // place pin beats the map center
        assertEquals("https://waze.com/ul?ll=19.4326077,-99.133208&navigate=yes", LinkParser.toWaze(
                "https://www.google.com/maps/place/Z%C3%B3calo/@19.43,-99.14,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d19.4326077!4d-99.133208!16s"));
        assertEquals("https://waze.com/ul?ll=19.4326,-99.1332&navigate=yes",
                LinkParser.toWaze("https://maps.google.com/?q=19.4326,-99.1332"));
        assertEquals("https://waze.com/ul?q=Av%20Reforma%20222&navigate=yes",
                LinkParser.toWaze("https://maps.google.com/maps?q=Av+Reforma+222&ftid=0x1"));
        assertEquals("https://waze.com/ul?q=Z%C3%B3calo%20CDMX&navigate=yes",
                LinkParser.toWaze("https://www.google.com/maps/place/Z%C3%B3calo+CDMX"));
        assertEquals("https://waze.com/ul?q=Aeropuerto%20CDMX&navigate=yes",
                LinkParser.toWaze("https://www.google.com/maps/dir/Mi+casa/Aeropuerto+CDMX/@19.4,-99.1,12z"));
        assertEquals("https://waze.com/ul?ll=19.4,-99.1&navigate=yes",
                LinkParser.toWaze("https://www.google.com/maps/@19.4,-99.1,15z"));
        assertNull(LinkParser.toWaze("https://waze.com/ul?q=x"));
        assertNull(LinkParser.toWaze("https://maps.app.goo.gl/abc"));
    }
}
