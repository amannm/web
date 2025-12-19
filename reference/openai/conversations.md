<a id="conversations"></a>
# Conversations

Create and manage conversations to store and retrieve conversation state across Response API calls.

<a id="conversations/create"></a>
## Create a conversation

**POST** `https://api.openai.com/v1/conversations`

Create a conversation.

#### Request body

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| items | array | Optional |  | Initial items to include in the conversation context. You may add up to 20 items at a time. |
| metadata | object or null | Optional |  | Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters. Values are strings with a maximum length of 512 characters. |

#### Returns

**Example request**

```bash
curl https://api.openai.com/v1/conversations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "metadata": {"topic": "demo"},
    "items": [
      {
        "type": "message",
        "role": "user",
        "content": "Hello!"
      }
    ]
  }'
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const conversation = await client.conversations.create({
  metadata: { topic: "demo" },
  items: [
    { type: "message", role: "user", content: "Hello!" }
  ],
});
console.log(conversation);
```

```python
from openai import OpenAI
client = OpenAI()

conversation = client.conversations.create(
  metadata={"topic": "demo"},
  items=[
    {"type": "message", "role": "user", "content": "Hello!"}
  ]
)
print(conversation)
```

```csharp
using System;
using System.Collections.Generic;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

Conversation conversation = client.CreateConversation(
    new CreateConversationOptions
    {
        Metadata = new Dictionary<string, string>
        {
            { "topic", "demo" }
        },
        Items =
        {
            new ConversationMessageInput
            {
                Role = "user",
                Content = "Hello!",
            }
        }
    }
);
Console.WriteLine(conversation.Id);
```

**Response**

```json
{
  "id": "conv_123",
  "object": "conversation",
  "created_at": 1741900000,
  "metadata": {"topic": "demo"}
}
```

<a id="conversations/retrieve"></a>
## Retrieve a conversation

**GET** `https://api.openai.com/v1/conversations/{conversation_id}`

Get a conversation

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation to retrieve. |

#### Returns

**Example request**

```bash
curl https://api.openai.com/v1/conversations/conv_123 \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const conversation = await client.conversations.retrieve("conv_123");
console.log(conversation);
```

```python
from openai import OpenAI
client = OpenAI()

conversation = client.conversations.retrieve("conv_123")
print(conversation)
```

```csharp
using System;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

Conversation conversation = client.GetConversation("conv_123");
Console.WriteLine(conversation.Id);
```

**Response**

```json
{
  "id": "conv_123",
  "object": "conversation",
  "created_at": 1741900000,
  "metadata": {"topic": "demo"}
}
```

<a id="conversations/update"></a>
## Update a conversation

**POST** `https://api.openai.com/v1/conversations/{conversation_id}`

Update a conversation

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation to update. |

#### Request body

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| metadata | map | Required |  | Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in a structured format, and querying for objects via API or the dashboard.<br><br>Keys are strings with a maximum length of 64 characters. Values are strings with a maximum length of 512 characters. |

#### Returns

**Example request**

```bash
curl https://api.openai.com/v1/conversations/conv_123 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "metadata": {"topic": "project-x"}
  }'
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const updated = await client.conversations.update(
  "conv_123",
  { metadata: { topic: "project-x" } }
);
console.log(updated);
```

```python
from openai import OpenAI
client = OpenAI()

updated = client.conversations.update(
  "conv_123",
  metadata={"topic": "project-x"}
)
print(updated)
```

```csharp
using System;
using System.Collections.Generic;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

Conversation updated = client.UpdateConversation(
    conversationId: "conv_123",
    new UpdateConversationOptions
    {
        Metadata = new Dictionary<string, string>
        {
            { "topic", "project-x" }
        }
    }
);
Console.WriteLine(updated.Id);
```

**Response**

```json
{
  "id": "conv_123",
  "object": "conversation",
  "created_at": 1741900000,
  "metadata": {"topic": "project-x"}
}
```

<a id="conversations/delete"></a>
## Delete a conversation

**DELETE** `https://api.openai.com/v1/conversations/{conversation_id}`

Delete a conversation. Items in the conversation will not be deleted.

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation to delete. |

#### Returns

**Example request**

```bash
curl -X DELETE https://api.openai.com/v1/conversations/conv_123 \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const deleted = await client.conversations.delete("conv_123");
console.log(deleted);
```

```python
from openai import OpenAI
client = OpenAI()

deleted = client.conversations.delete("conv_123")
print(deleted)
```

```csharp
using System;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

DeletedConversation deleted = client.DeleteConversation("conv_123");
Console.WriteLine(deleted.Id);
```

**Response**

```json
{
  "id": "conv_123",
  "object": "conversation.deleted",
  "deleted": true
}
```

<a id="conversations/list-items"></a>
## List items

**GET** `https://api.openai.com/v1/conversations/{conversation_id}/items`

List all items for a conversation with the given ID.

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation to list items for. |

#### Query parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| after | string | Optional |  | An item ID to list items after, used in pagination. |
| include | array | Optional |  | Specify additional output data to include in the model response. Currently supported values are:<br><br>- `web_search_call.action.sources`: Include the sources of the web search tool call.<br>- `code_interpreter_call.outputs`: Includes the outputs of python code execution in code interpreter tool call items.<br>- `computer_call_output.output.image_url`: Include image urls from the computer call output.<br>- `file_search_call.results`: Include the search results of the file search tool call.<br>- `message.input_image.image_url`: Include image urls from the input message.<br>- `message.output_text.logprobs`: Include logprobs with assistant messages.<br>- `reasoning.encrypted_content`: Includes an encrypted version of reasoning tokens in reasoning item outputs. This enables reasoning items to be used in multi-turn conversations when using the Responses API statelessly (like when the `store` parameter is set to `false`, or when an organization is enrolled in the zero data retention program). |
| limit | integer | Optional | Defaults to 20 | A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 20. |
| order | string | Optional |  | The order to return the input items in. Default is `desc`.<br><br>- `asc`: Return the input items in ascending order.<br>- `desc`: Return the input items in descending order. |

#### Returns

**Example request**

```bash
curl "https://api.openai.com/v1/conversations/conv_123/items?limit=10" \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const items = await client.conversations.items.list("conv_123", { limit: 10 });
console.log(items.data);
```

```python
from openai import OpenAI
client = OpenAI()

items = client.conversations.items.list("conv_123", limit=10)
print(items.data)
```

```csharp
using System;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

ConversationItemList items = client.ConversationItems.List(
    conversationId: "conv_123",
    new ListConversationItemsOptions { Limit = 10 }
);
Console.WriteLine(items.Data.Count);
```

**Response**

```json
{
  "object": "list",
  "data": [
    {
      "type": "message",
      "id": "msg_abc",
      "status": "completed",
      "role": "user",
      "content": [
        {"type": "input_text", "text": "Hello!"}
      ]
    }
  ],
  "first_id": "msg_abc",
  "last_id": "msg_abc",
  "has_more": false
}
```

<a id="conversations/create-items"></a>
## Create items

**POST** `https://api.openai.com/v1/conversations/{conversation_id}/items`

Create items in a conversation with the given ID.

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation to add the item to. |

#### Query parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| include | array | Optional |  | Additional fields to include in the response. See the `include` parameter for [listing Conversation items above](/docs/api-reference/conversations/list-items#conversations_list_items-include) for more information. |

#### Request body

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| items | array | Required |  | The items to add to the conversation. You may add up to 20 items at a time. |

#### Returns

**Example request**

```bash
curl https://api.openai.com/v1/conversations/conv_123/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "items": [
      {
        "type": "message",
        "role": "user",
        "content": [
          {"type": "input_text", "text": "Hello!"}
        ]
      },
      {
        "type": "message",
        "role": "user",
        "content": [
          {"type": "input_text", "text": "How are you?"}
        ]
      }
    ]
  }'
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const items = await client.conversations.items.create(
  "conv_123",
  {
    items: [
      {
        type: "message",
        role: "user",
        content: [{ type: "input_text", text: "Hello!" }],
      },
      {
        type: "message",
        role: "user",
        content: [{ type: "input_text", text: "How are you?" }],
      },
    ],
  }
);
console.log(items.data);
```

```python
from openai import OpenAI
client = OpenAI()

items = client.conversations.items.create(
  "conv_123",
  items=[
    {
      "type": "message",
      "role": "user",
      "content": [{"type": "input_text", "text": "Hello!"}],
    },
    {
      "type": "message",
      "role": "user",
      "content": [{"type": "input_text", "text": "How are you?"}],
    }
  ],
)
print(items.data)
```

```csharp
using System;
using System.Collections.Generic;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

ConversationItemList created = client.ConversationItems.Create(
    conversationId: "conv_123",
    new CreateConversationItemsOptions
    {
        Items = new List<ConversationItem>
        {
            new ConversationMessage
            {
                Role = "user",
                Content =
                {
                    new ConversationInputText { Text = "Hello!" }
                }
            },
            new ConversationMessage
            {
                Role = "user",
                Content =
                {
                    new ConversationInputText { Text = "How are you?" }
                }
            }
        }
    }
);
Console.WriteLine(created.Data.Count);
```

**Response**

```json
{
  "object": "list",
  "data": [
    {
      "type": "message",
      "id": "msg_abc",
      "status": "completed",
      "role": "user",
      "content": [
        {"type": "input_text", "text": "Hello!"}
      ]
    },
    {
      "type": "message",
      "id": "msg_def",
      "status": "completed",
      "role": "user",
      "content": [
        {"type": "input_text", "text": "How are you?"}
      ]
    }
  ],
  "first_id": "msg_abc",
  "last_id": "msg_def",
  "has_more": false
}
```

<a id="conversations/get-item"></a>
## Retrieve an item

**GET** `https://api.openai.com/v1/conversations/{conversation_id}/items/{item_id}`

Get a single item from a conversation with the given IDs.

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation that contains the item. |
| item_id | string | Required |  | The ID of the item to retrieve. |

#### Query parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| include | array | Optional |  | Additional fields to include in the response. See the `include` parameter for [listing Conversation items above](/docs/api-reference/conversations/list-items#conversations_list_items-include) for more information. |

#### Returns

**Example request**

```bash
curl https://api.openai.com/v1/conversations/conv_123/items/msg_abc \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const item = await client.conversations.items.retrieve(
  "conv_123",
  "msg_abc"
);
console.log(item);
```

```python
from openai import OpenAI
client = OpenAI()

item = client.conversations.items.retrieve("conv_123", "msg_abc")
print(item)
```

```csharp
using System;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

ConversationItem item = client.ConversationItems.Get(
    conversationId: "conv_123",
    itemId: "msg_abc"
);
Console.WriteLine(item.Id);
```

**Response**

```json
{
  "type": "message",
  "id": "msg_abc",
  "status": "completed",
  "role": "user",
  "content": [
    {"type": "input_text", "text": "Hello!"}
  ]
}
```

<a id="conversations/delete-item"></a>
## Delete an item

**DELETE** `https://api.openai.com/v1/conversations/{conversation_id}/items/{item_id}`

Delete an item from a conversation with the given IDs.

#### Path parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| conversation_id | string | Required |  | The ID of the conversation that contains the item. |
| item_id | string | Required |  | The ID of the item to delete. |

#### Returns

**Example request**

```bash
curl -X DELETE https://api.openai.com/v1/conversations/conv_123/items/msg_abc \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

```javascript
import OpenAI from "openai";
const client = new OpenAI();

const conversation = await client.conversations.items.delete(
  "conv_123",
  "msg_abc"
);
console.log(conversation);
```

```python
from openai import OpenAI
client = OpenAI()

conversation = client.conversations.items.delete("conv_123", "msg_abc")
print(conversation)
```

```csharp
using System;
using OpenAI.Conversations;

OpenAIConversationClient client = new(
    apiKey: Environment.GetEnvironmentVariable("OPENAI_API_KEY")
);

Conversation conversation = client.ConversationItems.Delete(
    conversationId: "conv_123",
    itemId: "msg_abc"
);
Console.WriteLine(conversation.Id);
```

**Response**

```json
{
  "id": "conv_123",
  "object": "conversation",
  "created_at": 1741900000,
  "metadata": {"topic": "demo"}
}
```

<a id="conversations/object"></a>
## The conversation object

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| created_at | integer | Required |  | The time at which the conversation was created, measured in seconds since the Unix epoch. |
| id | string | Required |  | The unique ID of the conversation. |
| metadata |  | Required |  | Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters. Values are strings with a maximum length of 512 characters. |
| object | string | Required |  | The object type, which is always `conversation`. |

<a id="conversations/list-items-object"></a>
## The item list

A list of Conversation items.

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| data | array | Required |  | A list of conversation items. |
| first_id | string | Required |  | The ID of the first item in the list. |
| has_more | boolean | Required |  | Whether there are more items available. |
| last_id | string | Required |  | The ID of the last item in the list. |
| object | string | Required |  | The type of object returned, must be `list`. |

Previous

Responses

Next

Streaming events
