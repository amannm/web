package com.amannmalik.web;

import java.io.IOException;

@FunctionalInterface
interface ThrowingConsumer<T> {
    void accept(T value) throws IOException;
}
