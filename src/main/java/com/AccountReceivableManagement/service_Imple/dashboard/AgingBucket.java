package com.AccountReceivableManagement.service_Imple.dashboard;

/** Aging buckets by days since invoice date (inclusive bounds). */
enum AgingBucket {
    CURRENT("current", "0–30 days", 0, 30),
    D60("d60", "31–60 days", 31, 60),
    D90("d90", "61–90 days", 61, 90),
    D120("d120", "91–120 days", 91, 120),
    D120_PLUS("d120plus", "Over 120 days", 121, null);

    final String key;
    final String label;
    final int minDays;
    final Integer maxDays;

    AgingBucket(String key, String label, int minDays, Integer maxDays) {
        this.key = key;
        this.label = label;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    /** Bucket for a non-negative age in days. */
    static AgingBucket forDays(long days) {
        for (AgingBucket bucket : values()) {
            if (bucket.maxDays == null || days <= bucket.maxDays) return bucket;
        }
        return D120_PLUS;
    }
}
