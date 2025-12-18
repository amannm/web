package com.amannmalik.web;

sealed interface Outcome permits CompletedOutcome, ToolCallOutcome {
}
