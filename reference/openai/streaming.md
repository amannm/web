<a id="responses-streaming"></a>
# Streaming events

When you [create a Response](/docs/api-reference/responses/create) with `stream` set to `true`, the server will emit server-sent events to the client as the Response is generated. This section contains the events that are emitted by the server.

[Learn more about streaming responses](/docs/guides/streaming-responses?api-mode=responses).

<a id="responses-streaming/response/created"></a>
### response.created

An event that is emitted when a response is created.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | The response that was created. |
| sequence_number | integer | Required |  | The sequence number for this event. |
| type | string | Required |  | The type of the event. Always `response.created`. |

**OBJECT response.created**

```json
{
  "type": "response.created",
  "response": {
    "id": "resp_67ccfcdd16748190a91872c75d38539e09e4d4aac714747c",
    "object": "response",
    "created_at": 1741487325,
    "status": "in_progress",
    "error": null,
    "incomplete_details": null,
    "instructions": null,
    "max_output_tokens": null,
    "model": "gpt-4o-2024-08-06",
    "output": [],
    "parallel_tool_calls": true,
    "previous_response_id": null,
    "reasoning": {
      "effort": null,
      "summary": null
    },
    "store": true,
    "temperature": 1,
    "text": {
      "format": {
        "type": "text"
      }
    },
    "tool_choice": "auto",
    "tools": [],
    "top_p": 1,
    "truncation": "disabled",
    "usage": null,
    "user": null,
    "metadata": {}
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/in_progress"></a>
### response.in_progress

Emitted when the response is in progress.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | The response that is in progress. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.in_progress`. |

**OBJECT response.in_progress**

```json
{
  "type": "response.in_progress",
  "response": {
    "id": "resp_67ccfcdd16748190a91872c75d38539e09e4d4aac714747c",
    "object": "response",
    "created_at": 1741487325,
    "status": "in_progress",
    "error": null,
    "incomplete_details": null,
    "instructions": null,
    "max_output_tokens": null,
    "model": "gpt-4o-2024-08-06",
    "output": [],
    "parallel_tool_calls": true,
    "previous_response_id": null,
    "reasoning": {
      "effort": null,
      "summary": null
    },
    "store": true,
    "temperature": 1,
    "text": {
      "format": {
        "type": "text"
      }
    },
    "tool_choice": "auto",
    "tools": [],
    "top_p": 1,
    "truncation": "disabled",
    "usage": null,
    "user": null,
    "metadata": {}
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/completed"></a>
### response.completed

Emitted when the model response is complete.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | Properties of the completed response. |
| sequence_number | integer | Required |  | The sequence number for this event. |
| type | string | Required |  | The type of the event. Always `response.completed`. |

**OBJECT response.completed**

```json
{
  "type": "response.completed",
  "response": {
    "id": "resp_123",
    "object": "response",
    "created_at": 1740855869,
    "status": "completed",
    "error": null,
    "incomplete_details": null,
    "input": [],
    "instructions": null,
    "max_output_tokens": null,
    "model": "gpt-4o-mini-2024-07-18",
    "output": [
      {
        "id": "msg_123",
        "type": "message",
        "role": "assistant",
        "content": [
          {
            "type": "output_text",
            "text": "In a shimmering forest under a sky full of stars, a lonely unicorn named Lila discovered a hidden pond that glowed with moonlight. Every night, she would leave sparkling, magical flowers by the water's edge, hoping to share her beauty with others. One enchanting evening, she woke to find a group of friendly animals gathered around, eager to be friends and share in her magic.",
            "annotations": []
          }
        ]
      }
    ],
    "previous_response_id": null,
    "reasoning_effort": null,
    "store": false,
    "temperature": 1,
    "text": {
      "format": {
        "type": "text"
      }
    },
    "tool_choice": "auto",
    "tools": [],
    "top_p": 1,
    "truncation": "disabled",
    "usage": {
      "input_tokens": 0,
      "output_tokens": 0,
      "output_tokens_details": {
        "reasoning_tokens": 0
      },
      "total_tokens": 0
    },
    "user": null,
    "metadata": {}
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/failed"></a>
### response.failed

An event that is emitted when a response fails.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | The response that failed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.failed`. |

**OBJECT response.failed**

```json
{
  "type": "response.failed",
  "response": {
    "id": "resp_123",
    "object": "response",
    "created_at": 1740855869,
    "status": "failed",
    "error": {
      "code": "server_error",
      "message": "The model failed to generate a response."
    },
    "incomplete_details": null,
    "instructions": null,
    "max_output_tokens": null,
    "model": "gpt-4o-mini-2024-07-18",
    "output": [],
    "previous_response_id": null,
    "reasoning_effort": null,
    "store": false,
    "temperature": 1,
    "text": {
      "format": {
        "type": "text"
      }
    },
    "tool_choice": "auto",
    "tools": [],
    "top_p": 1,
    "truncation": "disabled",
    "usage": null,
    "user": null,
    "metadata": {}
  }
}
```

<a id="responses-streaming/response/incomplete"></a>
### response.incomplete

An event that is emitted when a response finishes as incomplete.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | The response that was incomplete. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.incomplete`. |

**OBJECT response.incomplete**

```json
{
  "type": "response.incomplete",
  "response": {
    "id": "resp_123",
    "object": "response",
    "created_at": 1740855869,
    "status": "incomplete",
    "error": null,
    "incomplete_details": {
      "reason": "max_tokens"
    },
    "instructions": null,
    "max_output_tokens": null,
    "model": "gpt-4o-mini-2024-07-18",
    "output": [],
    "previous_response_id": null,
    "reasoning_effort": null,
    "store": false,
    "temperature": 1,
    "text": {
      "format": {
        "type": "text"
      }
    },
    "tool_choice": "auto",
    "tools": [],
    "top_p": 1,
    "truncation": "disabled",
    "usage": null,
    "user": null,
    "metadata": {}
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/output_item/added"></a>
#### response.output_item.added

Emitted when a new output item is added.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item | object | Required |  | The output item that was added. |
| output_index | integer | Required |  | The index of the output item that was added. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.output_item.added`. |

**OBJECT response.output_item.added**

```json
{
  "type": "response.output_item.added",
  "output_index": 0,
  "item": {
    "id": "msg_123",
    "status": "in_progress",
    "type": "message",
    "role": "assistant",
    "content": []
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/output_item/done"></a>
#### response.output_item.done

Emitted when an output item is marked done.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item | object | Required |  | The output item that was marked done. |
| output_index | integer | Required |  | The index of the output item that was marked done. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.output_item.done`. |

**OBJECT response.output_item.done**

```json
{
  "type": "response.output_item.done",
  "output_index": 0,
  "item": {
    "id": "msg_123",
    "status": "completed",
    "type": "message",
    "role": "assistant",
    "content": [
      {
        "type": "output_text",
        "text": "In a shimmering forest under a sky full of stars, a lonely unicorn named Lila discovered a hidden pond that glowed with moonlight. Every night, she would leave sparkling, magical flowers by the water's edge, hoping to share her beauty with others. One enchanting evening, she woke to find a group of friendly animals gathered around, eager to be friends and share in her magic.",
        "annotations": []
      }
    ]
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/content_part/added"></a>
#### response.content_part.added

Emitted when a new content part is added.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that was added. |
| item_id | string | Required |  | The ID of the output item that the content part was added to. |
| output_index | integer | Required |  | The index of the output item that the content part was added to. |
| part | object | Required |  | The content part that was added. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.content_part.added`. |

**OBJECT response.content_part.added**

```json
{
  "type": "response.content_part.added",
  "item_id": "msg_123",
  "output_index": 0,
  "content_index": 0,
  "part": {
    "type": "output_text",
    "text": "",
    "annotations": []
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/content_part/done"></a>
#### response.content_part.done

Emitted when a content part is done.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that is done. |
| item_id | string | Required |  | The ID of the output item that the content part was added to. |
| output_index | integer | Required |  | The index of the output item that the content part was added to. |
| part | object | Required |  | The content part that is done. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.content_part.done`. |

**OBJECT response.content_part.done**

```json
{
  "type": "response.content_part.done",
  "item_id": "msg_123",
  "output_index": 0,
  "content_index": 0,
  "sequence_number": 1,
  "part": {
    "type": "output_text",
    "text": "In a shimmering forest under a sky full of stars, a lonely unicorn named Lila discovered a hidden pond that glowed with moonlight. Every night, she would leave sparkling, magical flowers by the water's edge, hoping to share her beauty with others. One enchanting evening, she woke to find a group of friendly animals gathered around, eager to be friends and share in her magic.",
    "annotations": []
  }
}
```

<a id="responses-streaming/response/output_text/delta"></a>
#### response.output_text.delta

Emitted when there is an additional text delta.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that the text delta was added to. |
| delta | string | Required |  | The text delta that was added. |
| item_id | string | Required |  | The ID of the output item that the text delta was added to. |
| logprobs | array | Required |  | The log probabilities of the tokens in the delta. |
| output_index | integer | Required |  | The index of the output item that the text delta was added to. |
| sequence_number | integer | Required |  | The sequence number for this event. |
| type | string | Required |  | The type of the event. Always `response.output_text.delta`. |

**OBJECT response.output_text.delta**

```json
{
  "type": "response.output_text.delta",
  "item_id": "msg_123",
  "output_index": 0,
  "content_index": 0,
  "delta": "In",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/output_text/done"></a>
#### response.output_text.done

Emitted when text content is finalized.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that the text content is finalized. |
| item_id | string | Required |  | The ID of the output item that the text content is finalized. |
| logprobs | array | Required |  | The log probabilities of the tokens in the delta. |
| output_index | integer | Required |  | The index of the output item that the text content is finalized. |
| sequence_number | integer | Required |  | The sequence number for this event. |
| text | string | Required |  | The text content that is finalized. |
| type | string | Required |  | The type of the event. Always `response.output_text.done`. |

**OBJECT response.output_text.done**

```json
{
  "type": "response.output_text.done",
  "item_id": "msg_123",
  "output_index": 0,
  "content_index": 0,
  "text": "In a shimmering forest under a sky full of stars, a lonely unicorn named Lila discovered a hidden pond that glowed with moonlight. Every night, she would leave sparkling, magical flowers by the water's edge, hoping to share her beauty with others. One enchanting evening, she woke to find a group of friendly animals gathered around, eager to be friends and share in her magic.",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/refusal/delta"></a>
#### response.refusal.delta

Emitted when there is a partial refusal text.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that the refusal text is added to. |
| delta | string | Required |  | The refusal text that is added. |
| item_id | string | Required |  | The ID of the output item that the refusal text is added to. |
| output_index | integer | Required |  | The index of the output item that the refusal text is added to. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.refusal.delta`. |

**OBJECT response.refusal.delta**

```json
{
  "type": "response.refusal.delta",
  "item_id": "msg_123",
  "output_index": 0,
  "content_index": 0,
  "delta": "refusal text so far",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/refusal/done"></a>
#### response.refusal.done

Emitted when refusal text is finalized.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the content part that the refusal text is finalized. |
| item_id | string | Required |  | The ID of the output item that the refusal text is finalized. |
| output_index | integer | Required |  | The index of the output item that the refusal text is finalized. |
| refusal | string | Required |  | The refusal text that is finalized. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.refusal.done`. |

**OBJECT response.refusal.done**

```json
{
  "type": "response.refusal.done",
  "item_id": "item-abc",
  "output_index": 1,
  "content_index": 2,
  "refusal": "final refusal text",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/function_call_arguments/delta"></a>
#### response.function_call_arguments.delta

Emitted when there is a partial function-call arguments delta.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| delta | string | Required |  | The function-call arguments delta that is added. |
| item_id | string | Required |  | The ID of the output item that the function-call arguments delta is added to. |
| output_index | integer | Required |  | The index of the output item that the function-call arguments delta is added to. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.function_call_arguments.delta`. |

**OBJECT response.function_call_arguments.delta**

```json
{
  "type": "response.function_call_arguments.delta",
  "item_id": "item-abc",
  "output_index": 0,
  "delta": "{ \"arg\":"
  "sequence_number": 1
}
```

<a id="responses-streaming/response/function_call_arguments/done"></a>
#### response.function_call_arguments.done

Emitted when function-call arguments are finalized.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| arguments | string | Required |  | The function-call arguments. |
| item_id | string | Required |  | The ID of the item. |
| name | string | Required |  | The name of the function that was called. |
| output_index | integer | Required |  | The index of the output item. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  |  |

**OBJECT response.function_call_arguments.done**

```json
{
  "type": "response.function_call_arguments.done",
  "item_id": "item-abc",
  "name": "get_weather",
  "output_index": 1,
  "arguments": "{ \"arg\": 123 }",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/file_search_call/in_progress"></a>
#### response.file_search_call.in_progress

Emitted when a file search call is initiated.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the output item that the file search call is initiated. |
| output_index | integer | Required |  | The index of the output item that the file search call is initiated. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.file_search_call.in_progress`. |

**OBJECT response.file_search_call.in_progress**

```json
{
  "type": "response.file_search_call.in_progress",
  "output_index": 0,
  "item_id": "fs_123",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/file_search_call/searching"></a>
#### response.file_search_call.searching

Emitted when a file search is currently searching.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the output item that the file search call is initiated. |
| output_index | integer | Required |  | The index of the output item that the file search call is searching. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.file_search_call.searching`. |

**OBJECT response.file_search_call.searching**

```json
{
  "type": "response.file_search_call.searching",
  "output_index": 0,
  "item_id": "fs_123",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/file_search_call/completed"></a>
#### response.file_search_call.completed

Emitted when a file search call is completed (results found).

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the output item that the file search call is initiated. |
| output_index | integer | Required |  | The index of the output item that the file search call is initiated. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.file_search_call.completed`. |

**OBJECT response.file_search_call.completed**

```json
{
  "type": "response.file_search_call.completed",
  "output_index": 0,
  "item_id": "fs_123",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/web_search_call/in_progress"></a>
#### response.web_search_call.in_progress

Emitted when a web search call is initiated.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | Unique ID for the output item associated with the web search call. |
| output_index | integer | Required |  | The index of the output item that the web search call is associated with. |
| sequence_number | integer | Required |  | The sequence number of the web search call being processed. |
| type | string | Required |  | The type of the event. Always `response.web_search_call.in_progress`. |

**OBJECT response.web_search_call.in_progress**

```json
{
  "type": "response.web_search_call.in_progress",
  "output_index": 0,
  "item_id": "ws_123",
  "sequence_number": 0
}
```

<a id="responses-streaming/response/web_search_call/searching"></a>
#### response.web_search_call.searching

Emitted when a web search call is executing.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | Unique ID for the output item associated with the web search call. |
| output_index | integer | Required |  | The index of the output item that the web search call is associated with. |
| sequence_number | integer | Required |  | The sequence number of the web search call being processed. |
| type | string | Required |  | The type of the event. Always `response.web_search_call.searching`. |

**OBJECT response.web_search_call.searching**

```json
{
  "type": "response.web_search_call.searching",
  "output_index": 0,
  "item_id": "ws_123",
  "sequence_number": 0
}
```

<a id="responses-streaming/response/web_search_call/completed"></a>
#### response.web_search_call.completed

Emitted when a web search call is completed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | Unique ID for the output item associated with the web search call. |
| output_index | integer | Required |  | The index of the output item that the web search call is associated with. |
| sequence_number | integer | Required |  | The sequence number of the web search call being processed. |
| type | string | Required |  | The type of the event. Always `response.web_search_call.completed`. |

**OBJECT response.web_search_call.completed**

```json
{
  "type": "response.web_search_call.completed",
  "output_index": 0,
  "item_id": "ws_123",
  "sequence_number": 0
}
```

<a id="responses-streaming/response/reasoning_summary_part/added"></a>
#### response.reasoning_summary_part.added

Emitted when a new reasoning summary part is added.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the item this summary part is associated with. |
| output_index | integer | Required |  | The index of the output item this summary part is associated with. |
| part | object | Required |  | The summary part that was added. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| summary_index | integer | Required |  | The index of the summary part within the reasoning summary. |
| type | string | Required |  | The type of the event. Always `response.reasoning_summary_part.added`. |

**OBJECT response.reasoning_summary_part.added**

```json
{
  "type": "response.reasoning_summary_part.added",
  "item_id": "rs_6806bfca0b2481918a5748308061a2600d3ce51bdffd5476",
  "output_index": 0,
  "summary_index": 0,
  "part": {
    "type": "summary_text",
    "text": ""
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/reasoning_summary_part/done"></a>
#### response.reasoning_summary_part.done

Emitted when a reasoning summary part is completed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the item this summary part is associated with. |
| output_index | integer | Required |  | The index of the output item this summary part is associated with. |
| part | object | Required |  | The completed summary part. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| summary_index | integer | Required |  | The index of the summary part within the reasoning summary. |
| type | string | Required |  | The type of the event. Always `response.reasoning_summary_part.done`. |

**OBJECT response.reasoning_summary_part.done**

```json
{
  "type": "response.reasoning_summary_part.done",
  "item_id": "rs_6806bfca0b2481918a5748308061a2600d3ce51bdffd5476",
  "output_index": 0,
  "summary_index": 0,
  "part": {
    "type": "summary_text",
    "text": "**Responding to a greeting**\n\nThe user just said, \"Hello!\" So, it seems I need to engage. I'll greet them back and offer help since they're looking to chat. I could say something like, \"Hello! How can I assist you today?\" That feels friendly and open. They didn't ask a specific question, so this approach will work well for starting a conversation. Let's see where it goes from there!"
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/reasoning_summary_text/delta"></a>
#### response.reasoning_summary_text.delta

Emitted when a delta is added to a reasoning summary text.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| delta | string | Required |  | The text delta that was added to the summary. |
| item_id | string | Required |  | The ID of the item this summary text delta is associated with. |
| output_index | integer | Required |  | The index of the output item this summary text delta is associated with. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| summary_index | integer | Required |  | The index of the summary part within the reasoning summary. |
| type | string | Required |  | The type of the event. Always `response.reasoning_summary_text.delta`. |

**OBJECT response.reasoning_summary_text.delta**

```json
{
  "type": "response.reasoning_summary_text.delta",
  "item_id": "rs_6806bfca0b2481918a5748308061a2600d3ce51bdffd5476",
  "output_index": 0,
  "summary_index": 0,
  "delta": "**Responding to a greeting**\n\nThe user just said, \"Hello!\" So, it seems I need to engage. I'll greet them back and offer help since they're looking to chat. I could say something like, \"Hello! How can I assist you today?\" That feels friendly and open. They didn't ask a specific question, so this approach will work well for starting a conversation. Let's see where it goes from there!",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/reasoning_summary_text/done"></a>
#### response.reasoning_summary_text.done

Emitted when a reasoning summary text is completed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the item this summary text is associated with. |
| output_index | integer | Required |  | The index of the output item this summary text is associated with. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| summary_index | integer | Required |  | The index of the summary part within the reasoning summary. |
| text | string | Required |  | The full text of the completed reasoning summary. |
| type | string | Required |  | The type of the event. Always `response.reasoning_summary_text.done`. |

**OBJECT response.reasoning_summary_text.done**

```json
{
  "type": "response.reasoning_summary_text.done",
  "item_id": "rs_6806bfca0b2481918a5748308061a2600d3ce51bdffd5476",
  "output_index": 0,
  "summary_index": 0,
  "text": "**Responding to a greeting**\n\nThe user just said, \"Hello!\" So, it seems I need to engage. I'll greet them back and offer help since they're looking to chat. I could say something like, \"Hello! How can I assist you today?\" That feels friendly and open. They didn't ask a specific question, so this approach will work well for starting a conversation. Let's see where it goes from there!",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/reasoning_text/delta"></a>
#### response.reasoning_text.delta

Emitted when a delta is added to a reasoning text.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the reasoning content part this delta is associated with. |
| delta | string | Required |  | The text delta that was added to the reasoning content. |
| item_id | string | Required |  | The ID of the item this reasoning text delta is associated with. |
| output_index | integer | Required |  | The index of the output item this reasoning text delta is associated with. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `response.reasoning_text.delta`. |

**OBJECT response.reasoning_text.delta**

```json
{
  "type": "response.reasoning_text.delta",
  "item_id": "rs_123",
  "output_index": 0,
  "content_index": 0,
  "delta": "The",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/reasoning_text/done"></a>
#### response.reasoning_text.done

Emitted when a reasoning text is completed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| content_index | integer | Required |  | The index of the reasoning content part. |
| item_id | string | Required |  | The ID of the item this reasoning text is associated with. |
| output_index | integer | Required |  | The index of the output item this reasoning text is associated with. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| text | string | Required |  | The full text of the completed reasoning content. |
| type | string | Required |  | The type of the event. Always `response.reasoning_text.done`. |

**OBJECT response.reasoning_text.done**

```json
{
  "type": "response.reasoning_text.done",
  "item_id": "rs_123",
  "output_index": 0,
  "content_index": 0,
  "text": "The user is asking...",
  "sequence_number": 4
}
```

<a id="responses-streaming/response/image_generation_call/completed"></a>
#### response.image_generation_call.completed

Emitted when an image generation tool call has completed and the final image is available.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the image generation item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.image_generation_call.completed'. |

**OBJECT response.image_generation_call.completed**

```json
{
  "type": "response.image_generation_call.completed",
  "output_index": 0,
  "item_id": "item-123",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/image_generation_call/generating"></a>
#### response.image_generation_call.generating

Emitted when an image generation tool call is actively generating an image (intermediate state).

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the image generation item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of the image generation item being processed. |
| type | string | Required |  | The type of the event. Always 'response.image_generation_call.generating'. |

**OBJECT response.image_generation_call.generating**

```json
{
  "type": "response.image_generation_call.generating",
  "output_index": 0,
  "item_id": "item-123",
  "sequence_number": 0
}
```

<a id="responses-streaming/response/image_generation_call/in_progress"></a>
#### response.image_generation_call.in_progress

Emitted when an image generation tool call is in progress.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the image generation item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of the image generation item being processed. |
| type | string | Required |  | The type of the event. Always 'response.image_generation_call.in_progress'. |

**OBJECT response.image_generation_call.in_progress**

```json
{
  "type": "response.image_generation_call.in_progress",
  "output_index": 0,
  "item_id": "item-123",
  "sequence_number": 0
}
```

<a id="responses-streaming/response/image_generation_call/partial_image"></a>
#### response.image_generation_call.partial_image

Emitted when a partial image is available during image generation streaming.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the image generation item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| partial_image_b64 | string | Required |  | Base64-encoded partial image data, suitable for rendering as an image. |
| partial_image_index | integer | Required |  | 0-based index for the partial image (backend is 1-based, but this is 0-based for the user). |
| sequence_number | integer | Required |  | The sequence number of the image generation item being processed. |
| type | string | Required |  | The type of the event. Always 'response.image_generation_call.partial_image'. |

**OBJECT response.image_generation_call.partial_image**

```json
{
  "type": "response.image_generation_call.partial_image",
  "output_index": 0,
  "item_id": "item-123",
  "sequence_number": 0,
  "partial_image_index": 0,
  "partial_image_b64": "..."
}
```

<a id="responses-streaming/response/mcp_call_arguments/delta"></a>
#### response.mcp_call_arguments.delta

Emitted when there is a delta (partial update) to the arguments of an MCP tool call.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| delta | string | Required |  | A JSON string containing the partial update to the arguments for the MCP tool call. |
| item_id | string | Required |  | The unique identifier of the MCP tool call item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_call_arguments.delta'. |

**OBJECT response.mcp_call_arguments.delta**

```json
{
  "type": "response.mcp_call_arguments.delta",
  "output_index": 0,
  "item_id": "item-abc",
  "delta": "{",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/mcp_call_arguments/done"></a>
#### response.mcp_call_arguments.done

Emitted when the arguments for an MCP tool call are finalized.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| arguments | string | Required |  | A JSON string containing the finalized arguments for the MCP tool call. |
| item_id | string | Required |  | The unique identifier of the MCP tool call item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_call_arguments.done'. |

**OBJECT response.mcp_call_arguments.done**

```json
{
  "type": "response.mcp_call_arguments.done",
  "output_index": 0,
  "item_id": "item-abc",
  "arguments": "{\"arg1\": \"value1\", \"arg2\": \"value2\"}",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/mcp_call/completed"></a>
#### response.mcp_call.completed

Emitted when an MCP tool call has completed successfully.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the MCP tool call item that completed. |
| output_index | integer | Required |  | The index of the output item that completed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_call.completed'. |

**OBJECT response.mcp_call.completed**

```json
{
  "type": "response.mcp_call.completed",
  "sequence_number": 1,
  "item_id": "mcp_682d437d90a88191bf88cd03aae0c3e503937d5f622d7a90",
  "output_index": 0
}
```

<a id="responses-streaming/response/mcp_call/failed"></a>
#### response.mcp_call.failed

Emitted when an MCP tool call has failed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the MCP tool call item that failed. |
| output_index | integer | Required |  | The index of the output item that failed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_call.failed'. |

**OBJECT response.mcp_call.failed**

```json
{
  "type": "response.mcp_call.failed",
  "sequence_number": 1,
  "item_id": "mcp_682d437d90a88191bf88cd03aae0c3e503937d5f622d7a90",
  "output_index": 0
}
```

<a id="responses-streaming/response/mcp_call/in_progress"></a>
#### response.mcp_call.in_progress

Emitted when an MCP tool call is in progress.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the MCP tool call item being processed. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_call.in_progress'. |

**OBJECT response.mcp_call.in_progress**

```json
{
  "type": "response.mcp_call.in_progress",
  "sequence_number": 1,
  "output_index": 0,
  "item_id": "mcp_682d437d90a88191bf88cd03aae0c3e503937d5f622d7a90"
}
```

<a id="responses-streaming/response/mcp_list_tools/completed"></a>
#### response.mcp_list_tools.completed

Emitted when the list of available MCP tools has been successfully retrieved.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the MCP tool call item that produced this output. |
| output_index | integer | Required |  | The index of the output item that was processed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_list_tools.completed'. |

**OBJECT response.mcp_list_tools.completed**

```json
{
  "type": "response.mcp_list_tools.completed",
  "sequence_number": 1,
  "output_index": 0,
  "item_id": "mcpl_682d4379df088191886b70f4ec39f90403937d5f622d7a90"
}
```

<a id="responses-streaming/response/mcp_list_tools/failed"></a>
#### response.mcp_list_tools.failed

Emitted when the attempt to list available MCP tools has failed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the MCP tool call item that failed. |
| output_index | integer | Required |  | The index of the output item that failed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_list_tools.failed'. |

**OBJECT response.mcp_list_tools.failed**

```json
{
  "type": "response.mcp_list_tools.failed",
  "sequence_number": 1,
  "output_index": 0,
  "item_id": "mcpl_682d4379df088191886b70f4ec39f90403937d5f622d7a90"
}
```

<a id="responses-streaming/response/mcp_list_tools/in_progress"></a>
#### response.mcp_list_tools.in_progress

Emitted when the system is in the process of retrieving the list of available MCP tools.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The ID of the MCP tool call item that is being processed. |
| output_index | integer | Required |  | The index of the output item that is being processed. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.mcp_list_tools.in_progress'. |

**OBJECT response.mcp_list_tools.in_progress**

```json
{
  "type": "response.mcp_list_tools.in_progress",
  "sequence_number": 1,
  "output_index": 0,
  "item_id": "mcpl_682d4379df088191886b70f4ec39f90403937d5f622d7a90"
}
```

<a id="responses-streaming/response/code_interpreter_call/in_progress"></a>
#### response.code_interpreter_call.in_progress

Emitted when a code interpreter call is in progress.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the code interpreter tool call item. |
| output_index | integer | Required |  | The index of the output item in the response for which the code interpreter call is in progress. |
| sequence_number | integer | Required |  | The sequence number of this event, used to order streaming events. |
| type | string | Required |  | The type of the event. Always `response.code_interpreter_call.in_progress`. |

**OBJECT response.code_interpreter_call.in_progress**

```json
{
  "type": "response.code_interpreter_call.in_progress",
  "output_index": 0,
  "item_id": "ci_12345",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/code_interpreter_call/interpreting"></a>
#### response.code_interpreter_call.interpreting

Emitted when the code interpreter is actively interpreting the code snippet.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the code interpreter tool call item. |
| output_index | integer | Required |  | The index of the output item in the response for which the code interpreter is interpreting code. |
| sequence_number | integer | Required |  | The sequence number of this event, used to order streaming events. |
| type | string | Required |  | The type of the event. Always `response.code_interpreter_call.interpreting`. |

**OBJECT response.code_interpreter_call.interpreting**

```json
{
  "type": "response.code_interpreter_call.interpreting",
  "output_index": 4,
  "item_id": "ci_12345",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/code_interpreter_call/completed"></a>
#### response.code_interpreter_call.completed

Emitted when the code interpreter call is completed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| item_id | string | Required |  | The unique identifier of the code interpreter tool call item. |
| output_index | integer | Required |  | The index of the output item in the response for which the code interpreter call is completed. |
| sequence_number | integer | Required |  | The sequence number of this event, used to order streaming events. |
| type | string | Required |  | The type of the event. Always `response.code_interpreter_call.completed`. |

**OBJECT response.code_interpreter_call.completed**

```json
{
  "type": "response.code_interpreter_call.completed",
  "output_index": 5,
  "item_id": "ci_12345",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/code_interpreter_call_code/delta"></a>
#### response.code_interpreter_call_code.delta

Emitted when a partial code snippet is streamed by the code interpreter.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| delta | string | Required |  | The partial code snippet being streamed by the code interpreter. |
| item_id | string | Required |  | The unique identifier of the code interpreter tool call item. |
| output_index | integer | Required |  | The index of the output item in the response for which the code is being streamed. |
| sequence_number | integer | Required |  | The sequence number of this event, used to order streaming events. |
| type | string | Required |  | The type of the event. Always `response.code_interpreter_call_code.delta`. |

**OBJECT response.code_interpreter_call_code.delta**

```json
{
  "type": "response.code_interpreter_call_code.delta",
  "output_index": 0,
  "item_id": "ci_12345",
  "delta": "print('Hello, world')",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/code_interpreter_call_code/done"></a>
#### response.code_interpreter_call_code.done

Emitted when the code snippet is finalized by the code interpreter.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| code | string | Required |  | The final code snippet output by the code interpreter. |
| item_id | string | Required |  | The unique identifier of the code interpreter tool call item. |
| output_index | integer | Required |  | The index of the output item in the response for which the code is finalized. |
| sequence_number | integer | Required |  | The sequence number of this event, used to order streaming events. |
| type | string | Required |  | The type of the event. Always `response.code_interpreter_call_code.done`. |

**OBJECT response.code_interpreter_call_code.done**

```json
{
  "type": "response.code_interpreter_call_code.done",
  "output_index": 3,
  "item_id": "ci_12345",
  "code": "print('done')",
  "sequence_number": 1
}
```

<a id="responses-streaming/response/output_text/annotation/added"></a>
##### response.output_text.annotation.added

Emitted when an annotation is added to output text content.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| annotation | object | Required |  | The annotation object being added. (See annotation schema for details.) |
| annotation_index | integer | Required |  | The index of the annotation within the content part. |
| content_index | integer | Required |  | The index of the content part within the output item. |
| item_id | string | Required |  | The unique identifier of the item to which the annotation is being added. |
| output_index | integer | Required |  | The index of the output item in the response's output array. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always 'response.output_text.annotation.added'. |

**OBJECT response.output_text.annotation.added**

```json
{
  "type": "response.output_text.annotation.added",
  "item_id": "item-abc",
  "output_index": 0,
  "content_index": 0,
  "annotation_index": 0,
  "annotation": {
    "type": "text_annotation",
    "text": "This is a test annotation",
    "start": 0,
    "end": 10
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/queued"></a>
### response.queued

Emitted when a response is queued and waiting to be processed.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| response | object | Required |  | The full response object that is queued. |
| sequence_number | integer | Required |  | The sequence number for this event. |
| type | string | Required |  | The type of the event. Always 'response.queued'. |

**OBJECT response.queued**

```json
{
  "type": "response.queued",
  "response": {
    "id": "res_123",
    "status": "queued",
    "created_at": "2021-01-01T00:00:00Z",
    "updated_at": "2021-01-01T00:00:00Z"
  },
  "sequence_number": 1
}
```

<a id="responses-streaming/response/custom_tool_call_input/delta"></a>
#### response.custom_tool_call_input.delta

Event representing a delta (partial update) to the input of a custom tool call.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| delta | string | Required |  | The incremental input data (delta) for the custom tool call. |
| item_id | string | Required |  | Unique identifier for the API item associated with this event. |
| output_index | integer | Required |  | The index of the output this delta applies to. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The event type identifier. |

**OBJECT response.custom_tool_call_input.delta**

```json
{
  "type": "response.custom_tool_call_input.delta",
  "output_index": 0,
  "item_id": "ctc_1234567890abcdef",
  "delta": "partial input text"
}
```

<a id="responses-streaming/response/custom_tool_call_input/done"></a>
#### response.custom_tool_call_input.done

Event indicating that input for a custom tool call is complete.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| input | string | Required |  | The complete input data for the custom tool call. |
| item_id | string | Required |  | Unique identifier for the API item associated with this event. |
| output_index | integer | Required |  | The index of the output this event applies to. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The event type identifier. |

**OBJECT response.custom_tool_call_input.done**

```json
{
  "type": "response.custom_tool_call_input.done",
  "output_index": 0,
  "item_id": "ctc_1234567890abcdef",
  "input": "final complete input text"
}
```

<a id="responses-streaming/error"></a>
## error

Emitted when an error occurs.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| code | string | Required |  | The error code. |
| message | string | Required |  | The error message. |
| param | string | Required |  | The error parameter. |
| sequence_number | integer | Required |  | The sequence number of this event. |
| type | string | Required |  | The type of the event. Always `error`. |

**OBJECT error**

```json
{
  "type": "error",
  "code": "ERR_SOMETHING",
  "message": "Something went wrong",
  "param": null,
  "sequence_number": 1
}
```

Previous

Conversations

Next

Webhook Events
