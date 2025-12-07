```terminaloutput
$ web --help

Usage:
  web [GLOBAL OPTIONS] COMMAND [ARGS...]

Description:
  web sends minimal, structured instructions from the controller
  to a remote browser operator and returns normalized observations about
  the current web page.

Global options:
  -s, --session ID        Use an existing session ID (default: auto-create)
  -t, --timeout SEC       Per-command timeout in seconds (default: 20)
  -q, --quiet             Suppress non-essential info messages
  -v, --verbose           Print low-level protocol details
      --json              Emit machine-readable JSON responses
      --pretty            Pretty-print JSON (implies --json)
      --no-color          Disable ANSI colors in output
  -h, --help              Show this help and exit
  -V, --version           Show version and exit

Core concepts:
  - "Operator": the remote human at the browser (Person A).
  - "View": what is currently visible in the browser viewport.
  - "Element": a clickable or editable UI control identified by label, role,
               or positional index.
  - All commands are idempotent where possible and describe exactly one unit
    of intent (observe OR act, not both).

Available commands:
  session     Manage operator sessions (attach, detach, ping)
  status      Query high-level page identity and login state
  layout      Request structured descriptions of visible layout
  find        Locate UI elements by label, role, or text
  navigate    Perform navigation actions (click, back, reload, scroll)
  input       Edit a single form field or control
  inspect     Read text from the page (errors, dialogs, headings)
  verify      Check that specific text or values are present
  flow        Perform basic task-completion checks
  help        Show command-specific help

For command-specific help:
  web COMMAND --help


COMMAND DETAILS
===============

session
-------

Usage:
  web session open   [--note TEXT]
  web session close  [--force]
  web session info
  web session ping

Description:
  Manage the control channel to the operator. Exactly one active session
  is required for all other commands.

Options:
  --note TEXT    Optional human-readable note for the operator
  --force        Close session even if a command is running

Typical flows:
  - Open a new session:
      web session open --note "Support for account update"
  - Check liveness:
      web session ping


status
------

Usage:
  web status [--url] [--title] [--login] [--summary]

Description:
  Ask the operator to report basic page and session identity.

Options:
  --url          Return the current URL from the address bar
  --title        Return the current browser tab title
  --login        Return high-level login state and username if visible
  --summary      Return a short one-line description of the page

Notes:
  - If no flags are specified, all available fields are returned.

Example (JSON):
  web status --url --title --login --json

  {
    "url": "https://example.com/dashboard",
    "title": "Dashboard – Example Inc.",
    "login": { "state": "logged_in", "user": "alice@example.com" }
  }


layout
------

Usage:
  web layout [--regions] [--headings] [--buttons] [--fields]

Description:
  Ask the operator to summarize what is visible in the viewport.

Options:
  --regions      High-level description of top/left/center/right
  --headings     List visible headings in reading order
  --buttons      List main visible buttons with labels
  --fields       List visible form fields with labels

Example:
  web layout --regions --headings


find
----

Usage:
  web find [--label TEXT] [--text TEXT]
                 [--role ROLE] [--near TEXT] [--index N]

Description:
  Ask the operator to locate a single UI element.

Options:
  --label TEXT   Visible label on button/link/field (exact or close match)
  --text TEXT    Visible text inside the element (for links/buttons)
  --role ROLE    Logical role: button|link|tab|field|checkbox|radio|dropdown
  --near TEXT    Hint: nearby heading/section label to narrow the search
  --index N      Use N-th matching element (0-based)

Output (non-JSON):
  A short human-readable locator ID, e.g.:
    ELEMENT: button[label="Save"]@top-right

Output (--json):
  {
    "found": true,
    "handle": "e-42",
    "summary": "Button 'Save' in top-right of main panel"
  }

Notes:
  - The returned "handle" can be used by navigate/input/inspect/verify.


navigate
--------

Usage:
  web navigate click   (--label TEXT | --handle ID | --text TEXT)
  web navigate back
  web navigate reload
  web navigate scroll  (--top | --bottom | --up LINES | --down LINES)

Description:
  Instruct the operator to perform simple navigation actions.

Subcommands & options:

  click
    --label TEXT   Use visible label to identify element
    --text TEXT    Use visible text (for links/buttons)
    --handle ID    Use handle from `find` or prior output

  back
    No additional options.

  reload
    No additional options.

  scroll
    --top          Scroll to very top of page
    --bottom       Scroll to bottom of page
    --up LINES     Scroll up by an approximate number of lines
    --down LINES   Scroll down by an approximate number of lines

Examples:
  # Click a button labeled "Save changes"
  web navigate click --label "Save changes"

  # Scroll to the bottom of the page
  web navigate scroll --bottom


input
-----

Usage:
  web input text    (--label TEXT | --handle ID) --value STRING [--clear]
  web input check   (--label TEXT | --handle ID) (--on | --off)
  web input choice  (--label TEXT | --handle ID) --option TEXT
  web input submit  (--label TEXT | --handle ID)

Description:
  Instruct the operator to perform atomic form-related actions.

Subcommands & options:

  text
    --label TEXT   Field label (e.g. "Email", "Search")
    --handle ID    Handle from `find`
    --value STR    Value to type into the field
    --clear        Clear existing content before typing

  check
    --on           Ensure checkbox is checked
    --off          Ensure checkbox is unchecked

  choice
    --option TEXT  Dropdown or radio option label

  submit
    Typically used for forms with a primary submit button.

Examples:
  web input text --label "Search" --clear --value "invoice 2024"
  web input check --label "I agree to the terms" --on
  web input submit --label "Continue"


inspect
-------

Usage:
  web inspect errors   [--near TEXT]
  web inspect dialog   [--full]
  web inspect heading  [--level N]
  web inspect element  --handle ID

Description:
  Ask the operator to read specific pieces of text from the page.

Subcommands & options:

  errors
    --near TEXT   Limit to errors near a field or section with this label

  dialog
    --full        Include dialog title, body text, and button labels

  heading
    --level N     Restrict to heading level (e.g. 1 for h1, 2 for h2)

  element
    --handle ID   Read text of a specific element

Examples:
  web inspect errors
  web inspect dialog --full


verify
------

Usage:
  web verify text    [--contains TEXT] [--exact TEXT] [--near TEXT]
  web verify field   (--label TEXT | --handle ID) --value EXPECTED
  web verify present (--label TEXT | --handle ID)

Description:
  Ask the operator to confirm whether certain content or state is visible.

Subcommands & options:

  text
    --contains TEXT  True if any visible text contains TEXT
    --exact TEXT     True if any visible text equals TEXT
    --near TEXT      Limit check to region near this heading/label

  field
    --value EXPECTED Check if field content matches EXPECTED string

  present
    True if an element matching label/handle appears in the current view.

Exit codes:
  0  Verified true
  1  Verified false
  2  Indeterminate (operator unsure / conflicting cues)

Examples:
  web verify text --contains "Your changes have been saved"
  web verify field --label "Email" --value "user@example.com"


flow
----

Usage:
  web flow confirm [--text TEXT] [--id-label TEXT]
  web flow finish  [--logout-label TEXT]

Description:
  Higher-level task-completion checks using basic primitives.

Subcommands & options:

  confirm
    --text TEXT       Expected confirmation phrase (e.g. "Order placed")
    --id-label TEXT   Label of any confirmation ID (e.g. "Reference #")

  finish
    --logout-label TEXT  Label of logout/sign-out control (best guess if omitted)

Examples:
  web flow confirm --text "Your changes have been saved"
  web flow finish --logout-label "Sign out"


help
----

Usage:
  web help [COMMAND]

Description:
  Show general or command-specific help.

Examples:
  web help
  web help navigate


EXIT CODES
==========

For all commands except `verify` (see there for specifics):
  0  Command understood by operator and completed successfully
  1  Command understood but could not be completed (element not found, etc.)
  2  Command ambiguous; operator requested clarification
  3  Session error (no active operator / disconnected)
  4  Protocol error (invalid arguments, unknown command)
```