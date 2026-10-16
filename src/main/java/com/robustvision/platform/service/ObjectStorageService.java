package com.robustvision.platform.service;

public interface ObjectStorageService {
    void put(String key, byte[] content, String contentType);
    byte[] get(String key);
    boolean exists(String key);
    void delete(String key);
    /** Durable cleanup must surface failures so the queued job can retry. */
    default void deleteChecked(String key) { delete(key); }
    String backendName();
}
