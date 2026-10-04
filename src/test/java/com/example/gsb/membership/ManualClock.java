package com.example.gsb.membership;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

/** 测试用可变时钟，便于精确断言变更耗时。 */
final class ManualClock extends Clock {

    private long millis;

    ManualClock(long startMillis) {
        this.millis = startMillis;
    }

    @Override
    public ZoneId getZone() {
        return TimeZone.getTimeZone("UTC").toZoneId();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
        return millis;
    }

    void advance(long deltaMillis) {
        this.millis += deltaMillis;
    }
}
