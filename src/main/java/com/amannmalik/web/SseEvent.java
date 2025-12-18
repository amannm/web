package com.amannmalik.web;

record SseEvent(String event, String data, String lastEventId, Integer retryMillis) {
}
