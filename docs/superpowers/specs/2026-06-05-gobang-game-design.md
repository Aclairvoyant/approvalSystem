# Gobang Game Design

## Overview

This feature adds a new Gobang game for couples while leaving the existing flight chess implementation intact. The selected architecture is a separate Gobang domain model with its own tables, service, engine, WebSocket controller, frontend store, and board component. It reuses only the shared outer capabilities that are already proven in the project: authenticated users, partner relationship validation, mobile game navigation, room-style real-time topics, and common response conventions.

## Goals

- Let a user create a Gobang room with their related partner.
- Let the partner join by room code.
- Support standard 15x15 Gobang: black moves first, alternating turns, five connected stones wins.
- Support undo through a request/accept flow.
- Support surrender, immediately ending the game with the opponent as winner.
- Support full replay from the first move with playback controls.
- Add polished but restrained GSAP animations for stone placement, undo, win highlight, and replay.

## Non-Goals

- Do not rewrite the existing flight chess game.
- Do not reuse `games` and `game_moves` as the Gobang source of truth.
- Do not introduce AI opponents, matchmaking, ranked rules, forbidden-move variants, or spectator mode.
- Do not fix unrelated flight chess defects unless a shared route or UI change requires a narrow compatibility guard.

## Backend Architecture

Gobang uses separate persistence:

- `gobang_games`: room and current state.
- `gobang_moves`: append-only move and event log for replay.
- `gobang_undo_requests`: pending and resolved undo requests.

`gobang_games` stores `game_code`, `black_player_id`, `invited_player_id`, `white_player_id`, `current_turn`, `game_status`, `winner_id`, `board_data`, `last_move_id`, timestamps, and indexes for room code, players, invited player, status, and creation time. `white_player_id` stays null while the room is waiting; `invited_player_id` records the chosen partner so only that partner can join. `board_data` is a JSON snapshot with a 15x15 integer matrix where `0` means empty, `1` black, and `2` white, plus optional `lastMove` and `winningLine` metadata.

`gobang_moves` stores every durable event in order: `PLACE_STONE`, `UNDO`, `SURRENDER`, and `SYSTEM_END`. Each row includes `game_id`, `player_id`, `move_number`, `move_type`, `row_index`, `col_index`, `color`, `move_data`, and `created_at`. Replay reads this table ordered by `move_number`.

`gobang_undo_requests` stores one pending request at a time per active game. It records requester, responder, requested target move number, status, and timestamps. Accepting an undo appends an `UNDO` event, removes the last place-stone event from the effective board by rebuilding board state from accepted history, and switches turn back to the player whose move was undone.

Service boundaries:

- `IGobangService` handles room lifecycle, player validation, move orchestration, undo, surrender, replay query, and response conversion.
- `IGobangEngine` is pure game logic: empty board creation, coordinate validation, turn validation helper, place stone, win detection in four directions, board rebuild from moves.
- `GobangController` exposes REST APIs.
- `GobangWebSocketController` exposes real-time actions and broadcasts to `/topic/gobang/{gameId}`.

## API Contract

REST endpoints:

- `POST /api/gobang/create`: create room with `opponentUserId`.
- `POST /api/gobang/join`: join room with `gameCode`.
- `GET /api/gobang/{gameId}`: fetch detail and current board.
- `GET /api/gobang/list`: list current user's Gobang rooms by optional status.
- `GET /api/gobang/{gameId}/moves`: fetch ordered replay events.
- `POST /api/gobang/{gameId}/cancel`: owner cancels waiting room.

WebSocket destinations:

- `/app/gobang/{gameId}/place-stone` with `{ row, col }`.
- `/app/gobang/{gameId}/undo/request`.
- `/app/gobang/{gameId}/undo/respond` with `{ accepted }`.
- `/app/gobang/{gameId}/surrender`.
- `/app/gobang/{gameId}/sync`.
- `/app/gobang/{gameId}/heartbeat`.

Broadcast message types:

- `GOBANG_STATE_UPDATE`
- `GOBANG_STONE_PLACED`
- `GOBANG_UNDO_REQUESTED`
- `GOBANG_UNDO_RESOLVED`
- `GOBANG_PLAYER_SURRENDERED`
- `GOBANG_GAME_ENDED`
- `GOBANG_ERROR`

## Frontend Architecture

Keep the existing flight chess pages available. The game lobby becomes a multi-game entry with a segmented game selector. Creating a Gobang room calls `gobangApi.createGame`; joining can either use a Gobang-specific join dialog or select the game type before joining.

Add:

- `frontend/src/services/gobangApi.ts` for REST calls and types.
- `frontend/src/services/gobangWebsocket.ts` for STOMP actions.
- `frontend/src/store/modules/gobang.ts` for current game, board state, replay state, undo dialog state, and message reducers.
- `frontend/src/components/GobangBoard.vue` for the 15x15 board.
- `frontend/src/pages/mobile/GobangRoom.vue` for room UI, controls, dialogs, and replay controls.

Routes:

- Add `/mobile/gobang/room/:id`.
- Keep `/mobile/game/room/:id` for flight chess.

## GSAP Animation Design

Use GSAP inside `GobangBoard.vue` and replay controls only.

- Stone placement: `gsap.fromTo()` with `scale` and `autoAlpha`, scoped to the newly placed stone.
- Undo: animate the removed stone with `scale` and `autoAlpha`, then let Vue state remove it.
- Win highlight: animate the five winning stones with a short stagger and subtle `scale`.
- Replay: build a `gsap.timeline({ paused: true })` for visible replay steps and expose play, pause, restart, and seek controls from page state.

Vue lifecycle requirements:

- Create tweens after DOM update in `onMounted` or `nextTick`.
- Scope selectors with `gsap.context(..., boardRoot.value)`.
- Call `ctx.revert()` and kill replay timelines in `onUnmounted`.
- Respect `prefers-reduced-motion` through `gsap.matchMedia()` and reduce durations to zero when requested.
- Animate transform and `autoAlpha`, not layout properties such as width, height, top, or left.

## Rules And Edge Cases

- Only players in the Gobang game may act.
- Only the current player may place a stone.
- Coordinates must be 0-14 and the target cell must be empty.
- A game must be `PLAYING` to accept moves, undo, surrender, or heartbeat.
- A player cannot create another active Gobang room with the same invited partner if either side already has an unfinished Gobang game with the same pair.
- Undo requires the opponent's acceptance and only targets the latest effective place-stone move.
- Surrender cannot be undone.
- Replay is read-only and can include undo/surrender events, not only placement events.

## Testing Strategy

Backend tests first:

- `GobangEngineTest`: creates empty board, rejects invalid coordinates, detects horizontal, vertical, and diagonal five-in-a-row, does not falsely win with four.
- `GobangServiceTest`: creates room only for related users, joins by code, enforces turn order, rejects occupied cells, ends game on win, handles surrender winner, handles undo request acceptance, returns replay in move-number order.
- Controller/WebSocket focused tests where practical for auth/player validation and malformed payload handling.

Frontend verification:

- `npm run lint`
- `npm run build:check`
- Manual browser verification for create, join, place stone, undo, surrender, replay, reduced motion, mobile layout.

## Migration Plan

Add `V6__create_gobang_tables.sql` and update `database.sql` with the same schema for fresh installs. The migration must not alter flight chess tables. Foreign keys point to `users(id)` and Gobang tables reference their own game IDs. Index room code, player IDs, game status, and `(game_id, move_number)`.

## Review Risks

- Independent tables reduce coupling but duplicate some room lifecycle logic; keep service methods small and explicit.
- Replay must rebuild from effective events rather than trusting only board snapshots.
- WebSocket and REST response types must stay aligned with Pinia state.
- GSAP animations must be scoped and cleaned up or mobile navigation can leave running tweens.
