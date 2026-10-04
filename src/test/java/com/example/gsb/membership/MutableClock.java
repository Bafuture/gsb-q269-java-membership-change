package com.example.gsb.membership;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 测试用可调时钟。 */
final class MutableClock extends Clock {

    private long millis;

    MutableClock(long millis) {
        this.millis = millis;
    }

    void advance(long deltaMillis) {
        this.millis += deltaMillis;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }
}
