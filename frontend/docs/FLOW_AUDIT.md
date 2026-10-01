# SHARELOOP — audit business flow

NgĂ y audit: 2026-09-21; cáº­p nháº­t sau P0.1 cĂ¹ng ngĂ y. Pháº¡m vi: frontend, Redux mock state, route vĂ  dá»¯ liá»‡u seed. Báº£n audit ban Ä‘áº§u lĂ  static code trace; P0.1 Ä‘Ă£ cĂ³ kiá»ƒm chá»©ng domain tá»± Ä‘á»™ng nhÆ°ng chÆ°a pháº£i E2E trĂ¬nh duyá»‡t. Backend/API vĂ  xĂ¡c minh thanh toĂ¡n tháº­t náº±m ngoĂ i prototype; mock chá»‰ Ä‘Æ°á»£c tĂ­nh hoĂ n chá»‰nh khi state vĂ  quy táº¯c ná»‘i xuyĂªn mĂ n hĂ¬nh.

Quy Æ°á»›c: âœ… COMPLETE = UI + state + rule + transition + mock persistence + liĂªn káº¿t; đŸŸ¡ PARTIAL = cĂ³ Ä‘oáº¡n cháº¡y nhÆ°ng thiáº¿u bÆ°á»›c; âŒ MISSING = chÆ°a triá»ƒn khai; đŸ”´ INCORRECT = logic hiá»‡n táº¡i trĂ¡i yĂªu cáº§u hoáº·c cĂ³ Ä‘Æ°á»ng bypass. `approved` lĂ  tráº¡ng thĂ¡i public hiá»‡n táº¡i; khĂ´ng Ä‘á»“ng nghÄ©a toĂ n bá»™ vĂ²ng Ä‘á»i ACTIVE/RESERVED. DĂ²ng dÆ°á»›i Ä‘Ă¢y dĂ¹ng Ä‘Æ°á»ng dáº«n tÆ°Æ¡ng Ä‘á»‘i vĂ  sá»‘ dĂ²ng cá»§a báº£n audit.

| Flow | Requirement | Status | Evidence | Missing/Problem |
|------|-------------|--------|----------|-----------------|
| 1 | ÄÄƒng kĂ½ â†’ email OTP â†’ phone OTP â†’ kĂ­ch hoáº¡t | đŸ”´ INCORRECT | `src/pages/auth/Register.tsx:7-24,39-99`; `src/app/store.ts:24-60`; `src/types/domain.ts:20-40` | Chá»‰ cĂ³ má»™t mĂ n OTP phone, báº¥t ká»³ mĂ£ nĂ o (ká»ƒ cáº£ rá»—ng) cÅ©ng táº¡o user; khĂ´ng cĂ³ email Ä‘áº§u vĂ o, password Ä‘áº§u vĂ o, resend, mĂ£ OTP, `emailVerified`/`phoneVerified` hay tráº¡ng thĂ¡i unverified. Email tá»± táº¡o `@example.com`, password cá»‘ Ä‘á»‹nh. |
| 1 | Login, session, profile, logout, guard | đŸŸ¡ PARTIAL | `src/pages/auth/Login.tsx:13-27`; `src/pages/user/Profile.tsx:18-33,82-129,243-272`; `src/routes/Guards.tsx:9-24`; `src/utils/storage.ts:4-29`; `src/layouts/AppShell.tsx:119-124` | Login/profile/logout vĂ  localStorage hoáº¡t Ä‘á»™ng; profile khĂ´ng validation/verify khi Ä‘á»•i email-phone; lá»‹ch sá»­ uy tĂ­n lĂ  text giáº£. Password plaintext vĂ  session chá»‰ lĂ  `currentUserId` localStorage, khĂ´ng báº£o máº­t. |
| 2 | Ba sá»‘ dÆ°, giĂ¡ 1 Credit = 1.000Ä‘ | đŸŸ¡ PARTIAL | `src/types/domain.ts:30-35`; `src/utils/credit.ts`; `src/pages/user/Credit.tsx:82-107`; `src/layouts/AppShell.tsx:69` | TĂ¡ch total/available/hold; P0.1 kiá»ƒm tra nonnegative, safe integer vĂ  invariant sau Redux mutation. Header hiá»‡n available, trang vĂ­ hiá»‡n cáº£ ba. Váº«n chá»‰ lĂ  mock client-side. |
| 2 | QR â†’ pending â†’ admin confirm â†’ cá»™ng má»™t láº§n, ledger | đŸŸ¡ PARTIAL | `src/pages/user/Credit.tsx`; `src/app/store.ts`; `src/pages/admin/AdminPages.tsx:549-614` | P0.1 cháº·n VND khĂ´ng dÆ°Æ¡ng/khĂ´ng bá»™i 1.000, trĂ¹ng topup ID/code/ref, xĂ¡c nháº­n láº·p; admin session/role báº¯t buá»™c, adjustment Ă¢m khĂ´ng vÆ°á»£t available, ledger cĂ³ ref. QR váº«n lĂ  hĂ¬nh máº«u; chÆ°a cĂ³ payment verification/reject/failed action; UI lá»‹ch sá»­ chÆ°a hiá»ƒn thá»‹ ref/ID. |
| 3 | Form Ä‘Äƒng, áº£nh, GIVE/SWAP, duyá»‡t hai táº§ng | đŸ”´ INCORRECT | `src/pages/user/Post.tsx:22-41,76-140`; `src/app/store.ts:62-73,125-148`; `src/pages/admin/AdminPages.tsx:127-211` | Thiáº¿u áº£nh váº«n chĂ¨n áº£nh Unsplash; khĂ´ng cĂ³ hard-block tá»« khĂ³a/ná»™i dung/category/district á»Ÿ reducer; `forbidden_keywords` chá»‰ config seed. Admin cĂ³ list/tĂ³m táº¯t, approve/reject trá»±c tiáº¿p, khĂ´ng checklist, chi tiáº¿t riĂªng, lĂ½ do reject hay moderation record. |
| 3 | Chá»‰ bĂ i Ä‘Æ°á»£c duyá»‡t hiá»ƒn thá»‹ cĂ´ng khai | đŸŸ¡ PARTIAL | `src/pages/public/Home.tsx:133-145`; `src/pages/public/Browse.tsx:29-38`; `src/pages/public/AI.tsx:35-49`; `src/pages/public/ProductDetail.tsx:19-24,89-100` | CĂ¡c danh sĂ¡ch lá»c approved vĂ  nĂºt request cháº·n status khĂ¡c; truy cáº­p tháº³ng `/product/:id` váº«n xem Ä‘Æ°á»£c cáº£ pending/rejected/removed, khĂ´ng kiá»ƒm tra expiry theo thá»i gian. |
| 4 | Search/detail/request â†’ owner chá»n 1 â†’ transaction | đŸ”´ INCORRECT | `src/pages/public/Browse.tsx:29-38`; `src/pages/public/ProductDetail.tsx:28-34`; `src/app/store.ts` (`createTransaction`); `src/pages/user/Activities.tsx:22-23,129-185` | Search theo quáº­n hoáº¡t Ä‘á»™ng, nhÆ°ng request táº¡o transaction + conversation ngay; khĂ´ng cĂ³ Request entity/list, owner accept/reject/chá»n má»™t, trade khĂ´ng gáº¯n mĂ³n phĂ­a requester; nhiá»u request cĂ¹ng item cĂ³ thá»ƒ thĂ nh nhiá»u tx. P0.1 Ä‘Ă£ yĂªu cáº§u item approved vĂ  requester lĂ  session active; expiry/lock item váº«n thiáº¿u. |
| 4 | Ba chá»‘t kiá»ƒm tra Credit | đŸ”´ INCORRECT | `src/app/store.ts:150-186,207-220,231-245`; `src/utils/credit.ts:50-65,105-129` | CHECK 1 lĂºc gá»­i request: khĂ´ng. CHECK 2 lĂºc chá»n partner/táº¡o tx: khĂ´ng (cÅ©ng khĂ´ng cĂ³ chá»n partner). CHECK 3 táº¡i hold sau xĂ¡c nháº­n lá»‹ch: kiá»ƒm tra `availableCredit < fee` cho ngÆ°á»i thao tĂ¡c; lĂºc spend khĂ´ng recheck (Ä‘Ă£ hold). ÄĂ¢y lĂ  **má»™t** chá»‘t thá»±c táº¿, khĂ´ng pháº£i ba. |
| 5 | Chat riĂªng, lá»‹ch hai bĂªn, hold/charge/release | đŸŸ¡ PARTIAL | `src/app/store.ts`; `src/pages/user/Messages.tsx:27-50,60-80,122-209,239-445`; `src/utils/credit.ts` | Má»—i tx cĂ³ convId/transactionId vĂ  item/partner Ä‘á»™ng; lá»‹ch lÆ°u Redux. P0.1 Ä‘Ă£ cháº·n actor láº¡, tá»± accept lá»‹ch, hold cá»§a ngÆ°á»i khĂ´ng chá»‹u phĂ­ vĂ  chat vĂ o conv khĂ´ng thuá»™c mĂ¬nh; hold/spend/release dĂ¹ng ref chá»‘ng láº·p. ChÆ°a cĂ³ bÆ°á»›c owner chá»n request/item lock; lá»—i thiáº¿u Credit bá»‹ no-op á»Ÿ domain vĂ  UI chÆ°a giáº£i thĂ­ch. |
| 5 | State machine/fee an toĂ n | đŸŸ¡ PARTIAL | `src/utils/transaction.ts`; `src/app/store.ts`; `src/utils/credit.ts`; `scripts/verify-domain.mjs` | P0.1 Ă¡p dá»¥ng canonical transitions, COMPLETED terminal, cancel/spend/release idempotent, nonnegative wallet; 8 ca kiá»ƒm chá»©ng Ä‘áº¡t. Váº«n chÆ°a cĂ³ action tranh cháº¥p hoáº·c quy táº¯c refund tranh cháº¥p; UI chÆ°a cĂ³ thĂ´ng bĂ¡o khi action bá»‹ tá»« chá»‘i. |
| 6 | Evidence â†’ xĂ¡c nháº­n hai phĂ­a â†’ completed | đŸŸ¡ PARTIAL | `src/pages/user/Messages.tsx:392-443`; `src/types/domain.ts:72-90`; `src/app/store.ts`; `src/utils/credit.ts` | áº¢nh/video data URL vĂ  boolean hai phĂ­a lÆ°u theo tx; fee spend khi cáº£ hai. P0.1 yĂªu cáº§u Ä‘Ăºng participant/state; seed `tx_001` chuyá»ƒn WAITING_HANDOVER cho khá»›p hai payer Ä‘Ă£ hold (cĂ³ migration persisted state). Evidence khĂ´ng báº¯t buá»™c, khĂ´ng timestamp tá»«ng tá»‡p/xĂ¡c nháº­n; chÆ°a cáº­p nháº­t Item/reward/stars. |
| 6 | Report/dispute/resolution/TrustStars | âŒ MISSING | `src/types/domain.ts:135-144`; `src/mocks/database.ts:664-676`; `src/pages/admin/AdminPages.tsx:796-800`; `src/pages/user/Profile.tsx:243-272` | CĂ³ interface + máº£ng disputes rá»—ng vĂ  trang admin Ä‘á»c máº£ng, nhÆ°ng khĂ´ng action táº¡o report/dispute, chuyá»ƒn DISPUTED, resolution, trá»«/khĂ´i phá»¥c sao, reason/history/audit tÆ°Æ¡ng á»©ng. KhĂ´ng cĂ³ report listing/user/transaction. |
| 7 | AI two-way swap matching | đŸ”´ INCORRECT | `src/pages/public/AI.tsx:22-49,142-233` | Mode â€œhaveâ€ lá»c má»™t chiá»u category, khĂ´ng so mĂ³n user cĂ³ vá»›i `tradeFor` cá»§a hai bĂªn, khĂ´ng báº¯t buá»™c approved trade (cĂ³ thá»ƒ hiá»‡n gift), khĂ´ng táº¡o/gáº¯n mĂ³n user Ä‘Äƒng. Tá»‘i Ä‘a 5 chá»© khĂ´ng báº£o Ä‘áº£m 3â€“5 phĂ¹ há»£p. |
| 7 | `/ai` assistant ngĂ´n ngá»¯ tá»± nhiĂªn, áº£nh â†’ mĂ´ táº£ â†’ Ä‘á» nghá»‹ | đŸŸ¡ PARTIAL | `src/app/router.tsx:37-40`; `src/pages/public/AI.tsx:28-67,98-139,160-228` | CĂ¹ng Item dataset, parse district vĂ  vĂ i tá»« khĂ³a type; category khĂ´ng parse, tá»« khĂ³a item chá»‰ special-case â€œxe Ä‘áº¡pâ€; nĂºt â€œPhĂ¢n tĂ­ch nhu cáº§uâ€ khĂ´ng handler. áº¢nh + description chá»‰ local, mĂ´ táº£ cá»‘ Ä‘á»‹nh, khĂ´ng xĂ¡c nháº­n/táº¡o Item; Ä‘á» nghá»‹ gá»i createTransaction trá»±c tiáº¿p vĂ  cĂ³ thá»ƒ lĂ  gift. |
| 8 | Admin content/users/transactions/config/log | đŸŸ¡ PARTIAL | `src/app/router.tsx:60-86`; `src/pages/admin/AdminPages.tsx:127-211,215-393,396-547,616-827` | CĂ³ duyá»‡t, list user/lock/unlock, tx list/detail, fee setting, audit log. Category/district/keywords/ranks/expired/disputes/alerts chá»§ yáº¿u báº£ng read-only hoáº·c dá»¯ liá»‡u giáº£; khĂ´ng cĂ³ xá»­ lĂ½ tranh cháº¥p/report, sao, expiry, checklist; user detail cĂ³ Ä‘iá»u chá»‰nh Credit nhÆ°ng khĂ´ng TrustStars. |
| 8 | Finance/permission | đŸŸ¡ PARTIAL | `src/pages/admin/AdminPages.tsx:549-614`; `src/routes/Guards.tsx`; `src/app/store.ts` | Finance chá»‰ tá»•ng Credit/hold/pending vĂ  topup list; thiáº¿u tiá»n thá»±c thu, fee theo loáº¡i, date range/tuáº§n/thĂ¡ng/quĂ½/export, ledger tá»«ng user Ä‘áº§y Ä‘á»§. P0.1 kiá»ƒm tra admin active/session/role táº¡i reducer mutations liĂªn quan; Ä‘Ă¢y váº«n lĂ  client-side mock, khĂ´ng pháº£i authorization server. |
| 9 | Renew, GIVEâ†’SWAP, stars recovery | đŸŸ¡ PARTIAL | `src/app/store.ts:76-117`; `src/pages/user/Activities.tsx:79-112,187-230` | Renew +2 thĂ¡ng vĂ  Ä‘Æ°a vá» pending Ä‘á»ƒ duyá»‡t láº¡i; khĂ´ng cĂ³ gáº§n háº¿t háº¡n cáº£nh bĂ¡o/Ä‘iá»u kiá»‡n. Form sá»­a khĂ´ng Ä‘á»•i type; khĂ´ng cĂ³ GIVEâ†’SWAP hoáº·c recovery stars. |
| 10 | Alert, CSV, ACTIVEâ†’EXPIRED/admin xá»­ lĂ½ | đŸŸ¡ PARTIAL | `src/pages/admin/AdminPages.tsx:783-810`; `src/types/domain.ts:5`; `src/app/store.ts:98-108` | â€œAlertâ€ coi nhiá»u bĂªn hold á»Ÿ cĂ¹ng trade lĂ  báº¥t thÆ°á»ng dĂ¹ Ä‘Ă¢y lĂ  quy táº¯c bĂ¬nh thÆ°á»ng; khĂ´ng phĂ¡t hiá»‡n account liĂªn quan. KhĂ´ng CSV. CĂ³ `expired` type/báº£ng nhÆ°ng khĂ´ng job/action tá»± Ä‘á»™ng quĂ¡ háº¡n hay archive/remove tá»« admin. |
| 11 | Q&A chatbot, review/rating, leaderboard, Ä‘iá»ƒm háº¹n, regional stats | âŒ MISSING | `src/app/router.tsx:30-93`; `src/types/domain.ts:164-177`; `src/pages/public/AI.tsx:17-235`; `src/pages/public/Home.tsx:146-149,341-355` | `/ai` lĂ  tĂ¬m Ä‘á»“, khĂ´ng pháº£i chatbot Q&A; khĂ´ng entity/action review/rating/leaderboard/meeting point. Home cĂ³ Ä‘áº¿m item theo vĂ i quáº­n, khĂ´ng pháº£i thá»‘ng kĂª giao dá»‹ch/khu vá»±c. |

## Diá»…n giáº£i theo flow vĂ  Ä‘á» xuáº¥t (chÆ°a triá»ƒn khai)

1. **TĂ i khoáº£n.** Login, há»“ sÆ¡ (name/email/phone/district/avatar/rank/reward/stars), logout vĂ  session mock cháº¡y chung `users`/`currentUserId`. ÄÄƒng kĂ½ xĂ¡c minh lĂ  sai thá»±c cháº¥t; thĂªm input email/password, validation uniqueness/format, OTP email + phone cĂ³ mĂ£/háº¡n/resend, verified flags vĂ  chá»‰ kĂ­ch hoáº¡t sau hai bÆ°á»›c. P0.1 Ä‘Ă£ tá»« chá»‘i/xĂ³a session cá»§a user locked á»Ÿ guard; váº«n thiáº¿u auth server.
2. **Credit.** Ba sá»‘ dÆ° vĂ  lá»‹ch sá»­ náº±m cĂ¹ng Redux; topup pendingâ†’completed vĂ  ledger cáº­p nháº­t. Bá»• sung xĂ¡c thá»±c amount/idempotency Ä‘á»™c láº­p id, cĂ¡c tráº¡ng thĂ¡i reject, guard nonnegative, ledger ref/metadata hiá»ƒn thá»‹; QR mock pháº£i ghi rĂµ khĂ´ng pháº£i thanh toĂ¡n tháº­t. Giá»¯ Reward Points/rank/stars tĂ¡ch biá»‡t. `src/pages/user/Profile.tsx:131-240` cĂ²n má»™t topup UI cÅ© dĂ¹ng cĂ¹ng action nhÆ°ng mĂ£ chuyá»ƒn khoáº£n khĂ¡c `/credit`.
3. **ÄÄƒng bĂ i.** Form Ä‘Æ°a Item pending, admin approve, Home/Browse tháº¥y approved. Thiáº¿t káº¿ hard-block táº¡i domain action dĂ¹ng config chung, validate áº£nh tháº­t/field/category/district vĂ  tráº¡ng thĂ¡i BLOCKED hoáº·c lá»—i rĂµ; moderation checklist + lĂ½ do reject + log. Báº£o vá»‡ detail URL vĂ  háº¿t háº¡n.
4. **Request/match.** Browse/filter/detail ná»‘i item chung, nhÆ°ng request láº­p tá»©c thĂ nh tx. Cáº§n Request PENDINGâ†’ACCEPTED/REJECTED/CANCELLED, owner chá»‰ chá»n má»™t (atomic), trade chá»‰ rĂµ item Ä‘á»‘i á»©ng Ä‘Ă£ approved, kiá»ƒm tra Credit á»Ÿ ba chá»‘t domain, xá»­ lĂ½ duplicate/double click, khĂ³a/giá»¯ item cho tx Ä‘Æ°á»£c chá»n.
5. **Chat/lá»‹ch/phĂ­.** Conv riĂªng vĂ  handover persisted lĂ  pháº§n cháº¡y Ä‘Æ°á»£c. P0.1 Ä‘Ă£ thĂªm actor/state guards, Ä‘á»‘i tĂ¡c má»›i Ä‘á»“ng Ă½ lá»‹ch, chá»‰ payer hold, chá»‰ complete khi Ä‘á»§ payer, cancel/release khĂ´ng sau spend. Cáº§n UI pháº£n há»“i lá»—i thiáº¿u Credit (domain hiá»‡n tá»« chá»‘i an toĂ n nhÆ°ng no-op) vĂ  Request/item lock á»Ÿ P0 tiáº¿p theo.
6. **BĂ n giao/uy tĂ­n.** Confirmation vĂ  evidence data URL cĂ³, hoĂ n táº¥t thu fee. ThĂªm timestamp/evidence policy, report theo má»¥c tiĂªu, dispute action + admin resolution/refund/charge, item lifecycle vĂ  háº­u giao dá»‹ch reward/stars/history/log tĂ¡ch Credit.
7. **AI.** `/ai` dĂ¹ng dá»¯ liá»‡u Item chung nhÆ°ng matching chÆ°a hai chiá»u. Parse category/query phá»• quĂ¡t; swap chá»‰ approved trade vĂ  yĂªu cáº§u item nguá»“n Ä‘Ă£ Ä‘Äƒng/Ä‘Æ°á»£c duyá»‡t, kiá»ƒm tra nhu cáº§u hai bĂªn; áº£nh/mĂ´ táº£ xĂ¡c nháº­n pháº£i Ä‘i vĂ o Item tháº­t trÆ°á»›c khi Ä‘á» nghá»‹. KhĂ´ng tĂ­nh nĂºt hoáº·c copy cá»‘ Ä‘á»‹nh lĂ  AI hoáº¡t Ä‘á»™ng.
8. **Admin.** Guard route cĂ³; báº£ng nhiá»u má»¥c chá»‰ trĂ¬nh bĂ y. Chuyá»ƒn config/category/keyword/district/rank tá»« static sang state/action cĂ³ role check, thĂªm dispute/report/expiry/TrustStars workflows, tĂ i chĂ­nh tá»« topup vĂ  ledger cĂ³ bá»™ lá»c thá»i gian/user/fee, audit log má»i mutation.
9. **Optional.** Renew hiá»‡n luĂ´n pending ká»ƒ cáº£ removed; thĂªm Ä‘iá»u kiá»‡n tráº¡ng thĂ¡i vĂ  thĂ´ng bĂ¡o gáº§n háº¡n. Äá»•i giftâ†’trade pháº£i tĂ¡i duyá»‡t; recovery stars cáº§n policy má»™t láº§n vĂ  history.
10. **Optional.** Thay cáº£nh bĂ¡o hold hai phĂ­a báº±ng tĂ­n hiá»‡u báº¥t thÆ°á»ng thá»±c; thĂªm CSV vĂ  expiry transition/job + admin actions.
11. **Optional.** Chatbot há»i Ä‘Ă¡p, review/rating, leaderboard/Ä‘iá»ƒm háº¹n vĂ  thá»‘ng kĂª khu vá»±c chÆ°a cĂ³ domain flow; chá»‰ lĂ m sau completion Ä‘Ă¡ng tin cáº­y.

## Entity / state audit

`EXISTS` nghÄ©a lĂ  cĂ³ schema/state vĂ  Ă­t nháº¥t má»™t Ä‘Æ°á»ng sá»­ dá»¥ng, **khĂ´ng** kháº³ng Ä‘á»‹nh flow hoĂ n chá»‰nh; `PARTIAL` lĂ  chá»‰ gá»™p trong entity khĂ¡c hoáº·c read-only; `MISSING` lĂ  khĂ´ng cĂ³ model/action tÆ°Æ¡ng á»©ng.

| Entity | Mức | Implementation / giới hạn |
|---|---|---|
| User | EXISTS | `src/types/domain.ts:20-40`, `src/mocks/database.ts:13-112`, `src/app/store.ts:12-60` |
| Profile | PARTIAL | Fields trong User; `src/pages/user/Profile.tsx:18-129`, khĂ´ng verification riĂªng |
| Wallet | EXISTS | Ba fields User; `src/utils/credit.ts:18-24`, `src/pages/user/Credit.tsx:82-107` |
| CreditLedger | EXISTS | `CreditHistory`; `src/types/domain.ts:112-121`, `src/utils/credit.ts:26-47,50-129`, `src/app/store.ts:291-334` |
| Topup | EXISTS | `src/types/domain.ts:123-133`, `src/app/store.ts:264-310` |
| Item | EXISTS | `src/types/domain.ts:42-56`, `src/app/store.ts:62-108` |
| ItemModeration | PARTIAL | status trĂªn Item + audit log; `src/app/store.ts:125-148`; khĂ´ng record/checklist/reason |
| Request | MISSING | `createTransaction` trực tiếp `src/app/store.ts:150-186` |
| Transaction | EXISTS | `src/types/domain.ts:72-90`, `src/app/store.ts:150-245` |
| Conversation | EXISTS | `src/types/domain.ts:92-100`, `src/app/store.ts:168-185` |
| Message | EXISTS | `src/types/domain.ts:102-110`, `src/app/store.ts:247-261` |
| Schedule/Handover | EXISTS | `src/types/domain.ts:58-70`, `src/app/store.ts:188-214` |
| Evidence | PARTIAL | String data URLs trong Transaction; `src/types/domain.ts:84-85`, khĂ´ng record/timestamp riĂªng |
| Report | MISSING | KhĂ´ng model/action report; admin disputes chá»‰ Ä‘á»c |
| Dispute | PARTIAL | `src/types/domain.ts:135-144`, seed `disputes: []` `src/mocks/database.ts:665-666`; khĂ´ng táº¡o/xá»­ lĂ½ |
| TrustStarsHistory | MISSING | Chỉ `User.reputationStars`; `src/types/domain.ts:34` |
| RewardHistory | MISSING | Chỉ `User.rewardPoints`; `src/types/domain.ts:33` |
| AI Match | PARTIAL | Káº¿t quáº£ useMemo local; `src/pages/public/AI.tsx:35-49`, khĂ´ng entity/2-way |
| AdminAuditLog | EXISTS | `src/types/domain.ts:146-154`, `src/app/store.ts:137-146,299-309,325-333,342-369` (khĂ´ng bao phá»§ má»i action) |
| SystemConfig | PARTIAL | `settings` cĂ³ tx fee/keywords; categories/districts/ranks lĂ  constants/read-only; `src/mocks/database.ts:678-693`, `src/constants/domain.ts:15-44` |

## State machine audit

| Entity | Mapping hiện tại | Vấn đề transition |
|---|---|---|
| Item | `pending`â‰ˆPENDING_REVIEW â†’ `approved`â‰ˆACTIVE/public hoáº·c `rejected`; `expired`, `removed` cĂ³ trong type | KhĂ´ng DRAFT/BLOCKED/RESERVED/COMPLETED, khĂ´ng auto expiry. `updateItemStatus` nháº­n má»i status khĂ´ng guard (`src/app/store.ts:125-148`); update/renew Ä‘Æ°a pending tá»« báº¥t cá»© status (`:76-108`). |
| Request | KhĂ´ng tá»“n táº¡i | KhĂ´ng PENDING/ACCEPTED/REJECTED/CANCELLED, owner khĂ´ng chá»n. |
| Transaction | `NEGOTIATING` â†’ `SCHEDULE_PROPOSED` â†’ `SCHEDULE_CONFIRMED` â†’ `CREDIT_HELD` (payer Ä‘áº§u) â†’ `WAITING_HANDOVER` (Ä‘á»§ payer; gift Ä‘i qua CREDIT_HELD trong cĂ¹ng action) â†’ `SENDER_CONFIRMED`/`RECEIVER_CONFIRMED` â†’ `COMPLETED`; `CANCELLED`, `DISPUTED` type | P0.1 dĂ¹ng `canTransition`/`transitionTransaction`, COMPLETED terminal, confirm/cancel guard; seed/persisted `tx_001` Ä‘á»§ payer Ä‘Ă£ normalize WAITING_HANDOVER. ChÆ°a cĂ³ action `DISPUTED`/resolution. |
| Topup | `pending` â†’ `completed` qua admin; `confirming`, `failed` trong type | KhĂ´ng action chuyá»ƒn confirming/failed/rejected, chÆ°a validate amount/ID (`src/app/store.ts:264-310`). |
| Handover | `proposed` â†’ `confirmed` | Chá»‰ kiá»ƒm tra tx status, khĂ´ng kiá»ƒm tra ngÆ°á»i Ä‘á»“ng Ă½ lĂ  Ä‘á»‘i tĂ¡c (`src/app/store.ts:207-214`). |

## Cross-flow trace

`Post.addItem` â†’ cĂ¹ng `items` pending â†’ `AdminModeration.updateItemStatus` approved â†’ Home/Browse/AI lá»c cĂ¹ng `items` â†’ ProductDetail `createTransaction` â†’ cĂ¹ng `transactions` + `conversations` + `messages` â†’ Messages Ä‘á» xuáº¥t/accept `handovers` â†’ `holdFee`/`creditHistory` â†’ hai xĂ¡c nháº­n â†’ `spendHeldFee`/COMPLETED â†’ Credit history vĂ  AdminTransactions/AdminFinance Ä‘á»c cĂ¹ng state. `src/utils/storage.ts` persist state vĂ o má»™t localStorage key. ÄĂ¢y lĂ  lĂµi xuyĂªn trang thá»±c, khĂ´ng pháº£i má»—i trang mock riĂªng. P0.1 Ä‘Ă£ xá»­ lĂ½ actor guards vĂ  cancel sau completion; chuá»—i váº«n Ä‘á»©t á»Ÿ Request/owner selection, moderation hard-block vĂ  háº­u giao dá»‹ch TrustStars/reward/finance. Storage cĂ²n merge láº¡i seedItems bá»‹ thiáº¿u; UI `Messages` chá»n conversation Ä‘áº§u khi mount, link tá»« transaction khĂ´ng truyá»n transactionId nĂªn cĂ³ thá»ƒ má»Ÿ sai chat dĂ¹ dá»¯ liá»‡u conv riĂªng Ä‘Ăºng.

## A. REQUIRED FLOW SUMMARY

| Flow | Kết luận |
|---|---|
| 1 Auth/profile | đŸŸ¡ PARTIAL (OTP lĂ  Ä‘iá»ƒm đŸ”´ INCORRECT) |
| Credit model | ?? PARTIAL |
| 3 Listing/moderation | đŸ”´ INCORRECT |
| 4 Request/matching/3 checks | đŸ”´ INCORRECT |
| 5 Chat/schedule/free transaction | ?? PARTIAL (P0.1 da sua cancel/spend/state guards; request van thieu) |
| 6 Handover/dispute/stars | đŸŸ¡ PARTIAL (dispute/stars âŒ MISSING) |
| 7 AI matching/assistant | đŸ”´ INCORRECT |
| 8 Admin/finance | đŸŸ¡ PARTIAL |

**Tá»•ng sau P0.1: 0 COMPLETE, 5 PARTIAL, 0 MISSING toĂ n flow, 3 INCORRECT** (cĂ¡c nhĂ¡nh MISSING náº±m trong flow partial/incorrect).

## B. OPTIONAL FLOW SUMMARY

| Flow | Kết luận |
|---|---|
| 9 Renew/conversion/stars | đŸŸ¡ PARTIAL |
| 10 Alert/CSV/expiry | đŸŸ¡ PARTIAL |
| 11 Chatbot/review/ranking | ❌ MISSING |

## C. CRITICAL GAPS

1. KhĂ´ng cĂ³ Request vĂ  bÆ°á»›c owner chá»n má»™t partner; transaction Ä‘Æ°á»£c táº¡o ngay, duplicate/race vĂ  nhiá»u giao dá»‹ch trĂªn cĂ¹ng Item (`src/app/store.ts:150-186`).
2. Credit má»›i check táº¡i hold (chÆ°a Ä‘á»§ ba chá»‘t request/accept/hold); P0.1 Ä‘Ă£ cháº·n cancel sau COMPLETED, sá»‘ dÆ° Ă¢m, duplicate spend/release vĂ  actor láº¡ (`src/utils/credit.ts`, `src/app/store.ts`).
3. ÄÄƒng bĂ i khĂ´ng hard-block vĂ  admin thiáº¿u checklist/reject reason; detail URL khĂ´ng háº¡n cháº¿ non-approved (`src/pages/user/Post.tsx:22-41`, `src/pages/admin/AdminPages.tsx:127-211`, `src/pages/public/ProductDetail.tsx:19-24`).
4. KhĂ´ng cĂ³ dispute/report/resolution vĂ  evidence timestamp; completion khĂ´ng cáº­p nháº­t Item, Reward Points, TrustStars/history (`src/app/store.ts:221-245`, `src/types/domain.ts:72-90,135-144`).
5. Auth OTP váº«n giáº£; P0.1 Ä‘Ă£ kiá»ƒm tra actor/admin vĂ  session locked á»Ÿ client nhÆ°ng chÆ°a cĂ³ auth/authorization backend Ä‘Ă¡ng tin cáº­y (`src/pages/auth/Register.tsx:16-24`, `src/routes/Guards.tsx`, `src/app/store.ts`).

## D. IMPLEMENTATION PLAN (đề xuất, chưa sửa)

**P0 â€” cháº¡y end-to-end an toĂ n:** (1) **P0.1 Ä‘Ă£ triá»ƒn khai** domain actor/status/role, ID/ref/idempotency, invariant nonnegative, kiá»ƒm chá»©ng tĂ¡m ca, cháº·n cancel sau spend. (2) CĂ²n láº¡i: hard-block listing + moderation/checklist/approve/reject reason; báº£o vá»‡ public detail/expiry. (3) Request entity vĂ  owner accept má»™t ngÆ°á»i, khĂ³a Item, trade item Ä‘á»‘i á»©ng. (4) Ba Credit checks táº¡i request, accept/lock, trÆ°á»›c hold/charge; UI bĂ¡o insufficient balance vĂ  xá»­ lĂ½ duplicate request. (5) Báº±ng chá»©ng + timestamp, completion cáº­p nháº­t Item, ledger vĂ  admin report ná»n táº£ng.

### Kết quả kiểm chứng P0.1

`scripts/verify-domain.mjs` kiá»ƒm tra 8 ca báº¯t buá»™c: holdâ†’spend, holdâ†’cancel, cancel sau COMPLETED, double spend, double release, unrelated actor, adjustment Ă¢m quĂ¡ available, confirm topup hai láº§n. Domain hiá»‡n tá»« chá»‘i mutation khĂ´ng há»£p lá»‡ báº±ng no-op (khĂ´ng bĂ¡o lá»—i UI). `npm run lint` vĂ  `npm run build` Ä‘Æ°á»£c cháº¡y láº¡i sau thay Ä‘á»•i; chÆ°a cĂ³ E2E trĂ¬nh duyá»‡t.

**P1 â€” hoĂ n thiá»‡n REQUIRED:** (6) Email/phone OTP mock tháº­t, resend, verified state/validation/session policy. (7) Report/dispute/admin resolution/refund vĂ  TrustStars/Reward histories cĂ³ lĂ½ do/log. (8) Two-way approved trade matching vĂ  assistant parse category/district/type, áº£nh/mĂ´ táº£ xĂ¡c nháº­n vĂ o Item tháº­t. (9) Admin config/content/finance theo ledger, fee type/date/user/week/month/quarter, phĂ¢n quyá»n action; topup reject vĂ  lá»‹ch sá»­ cĂ³ ref.

**P2 â€” OPTIONAL:** (10) Renew cáº£nh bĂ¡o/Ä‘iá»u kiá»‡n, giftâ†’trade re-review, stars recovery policy. (11) Expiry job/admin xá»­ lĂ½, abnormal multi-account alerts, CSV. (12) Q&A chatbot, post-transaction review/rating, leaderboard, Ä‘iá»ƒm háº¹n vĂ  thá»‘ng kĂª khu vá»±c.
