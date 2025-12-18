package com.amannmalik.web;

import java.util.Objects;

record ContentKey(String itemId, int contentIndex) {
    ContentKey {
        Objects.requireNonNull(itemId, "itemId");
    }
}
