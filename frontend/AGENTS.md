# SHARELOOP Agent Guide

SHARELOOP is a React + TypeScript + Vite frontend prototype using Tailwind, Redux Toolkit, React Redux, and React Router DOM.

## Architecture

- `src/types` contains domain contracts.
- Business data comes from the backend through `src/services/*Api.ts` and the shared axios client in `src/services/http.ts`. Pages call these services directly. There is no seed or mock database.
- `src/app/store.ts` is a small Redux store that only keeps the signed-in session (`currentUser`). Do not add business data or reducers to it. Business data always comes from the backend via `src/services/*Api.ts`.
- `src/utils/validation.ts` holds input checks (email, phone, password rules). Passwords and OTPs are never hashed or generated in the browser.
- `src/utils/reputation.ts` holds rank labels and reputation formatting helpers. It does not calculate ranks or points; the backend does.
- `src/components/ui.tsx` contains reusable SHARELOOP UI primitives.
- `src/pages` contains route screens.
- `src/routes/Guards.tsx` enforces route access.

## Design System Rules

Use SHARELOOP tokens from `tailwind.config.js` and `src/styles/globals.css`: primary `#00685F`, secondary `#9D4300`, background `#F8F9FF`, surface tokens, Be Vietnam Pro, radius and shadows from the design system.

Never redesign existing SHARELOOP UI without instruction.

## Domain Boundaries

Never duplicate domain entities. Home, Browse, AI, Activities, Messages, and Admin must resolve data from the same Item, User, Transaction, Conversation, Wallet/Credit history sources.

Never hardcode one transaction into every chat. Each `Conversation.transactionId` must resolve its own transaction.

## Routing And Roles

Roles are `guest`, `user`, and `admin`. Guest-only pages redirect logged-in users to `/`. Protected pages redirect guests to `/login`. Admin pages redirect guests to `/login` and non-admin users to `/`.

Never expose Admin features to normal users.

## Credit Invariants

Credit balances are managed by the backend. The frontend only calls the API and displays the result. Never calculate, change, or charge Credit in the browser, and never trigger a Credit action during render, page open, modal open, or conversation open.

Keep Credit, Reward Points, Member Rank, and Reputation Stars separate. Never mix Credit with Reward Points.

Display the balance exactly as the backend returns it (`totalCredit = availableCredit + holdCredit`).

## Transaction State Machine

Transaction status is managed by the backend. The frontend only calls the API and displays the status. Never change a status or decide whether a transition is allowed in the browser. Valid flow:

`NEGOTIATING -> SCHEDULE_PROPOSED -> SCHEDULE_CONFIRMED -> CREDIT_HELD -> SENDER_CONFIRMED/RECEIVER_CONFIRMED -> COMPLETED`

Alternative terminal states: `CANCELLED`, `DISPUTED`.

## Search Rules

District search only. Never introduce GPS, kilometers, radius, or near-me logic.

## Adding Pages

Add a route in `src/app/router.tsx`, reuse `AppShell` or `AdminLayout`, and load data by calling `src/services/*Api.ts`. Do not create page-local mock databases.

## Adding Components

Use existing tokens, Material Symbols, and component variants. Add reusable controls to `src/components/ui.tsx` or split them into a focused component folder if they grow.
