package com.sumologic.jenkins.jenkinssumologicplugin.utility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

/**
 * Thread-safe replacement for the Commons Lang {@code FastDateFormat} that used to format event
 * timestamps, keeping the same {@code Date} / {@code long} / {@code Calendar} overloads so call
 * sites are unchanged.
 * <p>
 * The pattern is {@code yyyy-MM-dd HH:mm:ss,SSS xxx}, which produces byte-identical output to the
 * old {@code yyyy-MM-dd HH:mm:ss,SSS ZZZZ} FastDateFormat pattern. Note {@code xxx} rather than
 * {@code XXX}: Commons Lang renders a zero offset as {@code +00:00}, whereas {@code XXX} would
 * render it as {@code Z}.
 */
public final class SumoDateFormat {

    private final DateTimeFormatter formatter;

    public SumoDateFormat(final String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    public String format(final Date date) {
        return format(date.toInstant());
    }

    public String format(final long millis) {
        return format(Instant.ofEpochMilli(millis));
    }

    public String format(final Calendar calendar) {
        return formatter.format(ZonedDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId()));
    }

    private String format(final Instant instant) {
        return formatter.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
}
