package com.jianzuo.writing;

/** Domain failures retain their original diagnostic text and a stable presentation key. */
final class UserError {
    interface Tagged { String key(); }
    static final class Invalid extends IllegalArgumentException implements Tagged {
        private final String key;
        Invalid(String key, String message) { super(message); this.key = key; }
        public String key() { return key; }
    }
    static final class Io extends java.io.IOException implements Tagged {
        private final String key;
        Io(String key, String message) { super(message); this.key = key; }
        Io(String key, String message, Throwable cause) { super(message, cause); this.key = key; }
        public String key() { return key; }
    }
    static final class Cancelled extends java.io.InterruptedIOException implements Tagged {
        private final String key;
        Cancelled(String key, String message) { super(message); this.key = key; }
        public String key() { return key; }
    }
    static final class Json extends org.json.JSONException implements Tagged {
        private final String key;
        Json(String key, String message) { super(message); this.key = key; }
        public String key() { return key; }
    }
    private UserError() {}
}
