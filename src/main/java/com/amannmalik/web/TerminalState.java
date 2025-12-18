package com.amannmalik.web;

enum TerminalState {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    INCOMPLETE;

    boolean isDone() {
        return this != IN_PROGRESS;
    }
}
