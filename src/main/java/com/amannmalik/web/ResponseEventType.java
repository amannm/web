package com.amannmalik.web;

import java.util.Arrays;

/**
 * Enumerates the server-sent event types emitted by the OpenAI Responses
 * streaming API (see reference/openai/streaming.md). Keeping the
 * vocabulary explicit reduces the odds of silent string typos and makes it
 * obvious which events are intentionally ignored.
 */
enum ResponseEventType {

    OUTPUT_TEXT_DELTA("response.output_text.delta"),
    OUTPUT_TEXT_DONE("response.output_text.done"),
    OUTPUT_TEXT_ANNOTATION_ADDED("response.output_text.annotation.added"),

    REFUSAL_DELTA("response.refusal.delta"),
    REFUSAL_DONE("response.refusal.done"),

    REASONING_TEXT_DELTA("response.reasoning_text.delta"),
    REASONING_TEXT_DONE("response.reasoning_text.done"),
    REASONING_SUMMARY_TEXT_DELTA("response.reasoning_summary_text.delta"),
    REASONING_SUMMARY_TEXT_DONE("response.reasoning_summary_text.done"),

    CONTENT_PART_ADDED("response.content_part.added"),
    CONTENT_PART_DONE("response.content_part.done"),

    OUTPUT_ITEM_ADDED("response.output_item.added"),
    OUTPUT_ITEM_DONE("response.output_item.done"),

    CUSTOM_TOOL_CALL_INPUT_DELTA("response.custom_tool_call_input.delta"),
    CUSTOM_TOOL_CALL_INPUT_DONE("response.custom_tool_call_input.done"),

    COMPLETED("response.completed"),
    FAILED("response.failed"),
    INCOMPLETE("response.incomplete"),
    CREATED("response.created"),
    QUEUED("response.queued"),
    IN_PROGRESS("response.in_progress"),
    ERROR("error"),

    // Built-in tool families we currently ignore but keep for completeness.
    FILE_SEARCH("response.file_search_call.in_progress", true),
    FILE_SEARCH_SEARCHING("response.file_search_call.searching", true),
    FILE_SEARCH_COMPLETED("response.file_search_call.completed", true),

    WEB_SEARCH("response.web_search_call.in_progress", true),
    WEB_SEARCH_SEARCHING("response.web_search_call.searching", true),
    WEB_SEARCH_COMPLETED("response.web_search_call.completed", true),

    IMAGE_GEN_IN_PROGRESS("response.image_generation_call.in_progress", true),
    IMAGE_GEN_GENERATING("response.image_generation_call.generating", true),
    IMAGE_GEN_PARTIAL("response.image_generation_call.partial_image", true),
    IMAGE_GEN_COMPLETED("response.image_generation_call.completed", true),

    CODE_INTERPRETER_IN_PROGRESS("response.code_interpreter_call.in_progress", true),
    CODE_INTERPRETER_INTERPRETING("response.code_interpreter_call.interpreting", true),
    CODE_INTERPRETER_COMPLETED("response.code_interpreter_call.completed", true),
    CODE_INTERPRETER_CODE_DELTA("response.code_interpreter_call_code.delta", true),
    CODE_INTERPRETER_CODE_DONE("response.code_interpreter_call_code.done", true),

    MCP_CALL_IN_PROGRESS("response.mcp_call.in_progress", true),
    MCP_CALL_COMPLETED("response.mcp_call.completed", true),
    MCP_CALL_FAILED("response.mcp_call.failed", true),
    MCP_CALL_ARGUMENTS_DELTA("response.mcp_call_arguments.delta", true),
    MCP_CALL_ARGUMENTS_DONE("response.mcp_call_arguments.done", true),
    MCP_LIST_TOOLS_IN_PROGRESS("response.mcp_list_tools.in_progress", true),
    MCP_LIST_TOOLS_COMPLETED("response.mcp_list_tools.completed", true),
    MCP_LIST_TOOLS_FAILED("response.mcp_list_tools.failed", true),

    FUNCTION_CALL_ARGUMENTS_DELTA("response.function_call_arguments.delta", true),
    FUNCTION_CALL_ARGUMENTS_DONE("response.function_call_arguments.done", true),

    UNKNOWN("<unknown>", true);

    private final String wireName;
    private final boolean ignorable;

    ResponseEventType(String wireName) {
        this(wireName, false);
    }

    ResponseEventType(String wireName, boolean ignorable) {
        this.wireName = wireName;
        this.ignorable = ignorable;
    }

    boolean isIgnorable() {
        return ignorable;
    }

    static ResponseEventType from(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(t -> t.wireName.equals(rawType))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
