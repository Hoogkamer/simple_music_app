# TODO — Pending Requirements & Tasks

This file contains pending requirements, bugs, and technical tasks that need to be addressed.

## General Guidelines

- Implement in the order you like, unless a priority like `[HIGH]` is specified.
- Use `[ ]` for pending, `[/]` for in-progress, `[-]` for blocked, and `[x]` for completed.
- Ask questions if something is unclear before starting.

## Definition of Done (DoD)

For every completed item, ensure:

- [ ] Code compiles without errors and remains clean/DRY.
- [ ] UI and UX remain consistent with the rest of the app.
- [ ] Tests are updated/added, run locally, and pass.
- [ ] `architecture.md` (especially Chapter 6) is updated if necessary.
- [ ] `user_manual.md` is updated for any user-facing changes.

## Bugs

<!-- Try to include: Expected vs Actual behavior, Steps to reproduce -->

### Radio module

- [ ]

### Music module

### Podcast module

- [ ]

### Common module

- [ ]

## New Features

<!-- Try to include: Acceptance Criteria -->

### Radio module

- [ ]

### Music module

### Podcast module

- [ ]

### Common module

- [x] In the widget we have fast forward and rewind buttons. For podcast they should skip 30 sec forwards, and 15 backwards, which is already there. For music and radio they should not be there.
- [x] add skip next and previous buttons to the widget, for podcast they should skip to the next/previous episode in the Recent list. For music they should go to the next/previous song in the playlist. For radio they should go to the next/previous station in the list.
- [x] in the widget show also the podcast (show title, and episode title), music (song title, and artist), and radio (station name).
- [x] in the widget show the icons we use at the bottom of the player window for the different media types.
- [x] when clicking on the widget it should open the app in the tab of which is currently active (playing or paused)

## Technical Debt & Chores

<!-- For refactoring, dependency updates, or internal improvements -->

### Radio module

- [ ]

### Music module

- [ ]

### Podcast module

- [ ]

### Common module

- [ ]
