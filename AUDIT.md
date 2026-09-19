# NutriVerse audit

Audit date: 2026-09-18. Existing local changes in CORS, profile, JWT, USDA, App and global styles were present before this audit and are being preserved. Findings below were recorded before implementation. No live database content or historical provenance has been certified.

## Phase 1 — Critical

| Severity | File / affected files | Problem and why it matters | Recommended fix |
|---|---|---|---|
| Critical | `controller/{Dashboard,MealLog,WaterLog,Nutrition,Chat}Controller.java`, `dto/{ChatRequest,NutritionMealRequest}.java`; corresponding frontend calls | Caller IDs control data access/writes without JWT validation. Chat uses conversation ID as profile owner and history key. | Authenticate all private API routes once; derive identity from validated claims; remove caller IDs; require ADMIN for KG inspection. |
| Critical | `service/JwtService.java`, configuration | Signing secret is hardcoded. Anyone with it can forge users/roles. | Require a strong environment secret, validate signed claims/expiry, invalidate old tokens by replacing the secret. |
| High | `controller/WaterLogController.java`, `service/WaterLogService.java` | Binding a persistence entity permits caller IDs/timestamps/document IDs; nonfinite quantities and old null amounts are unsafe. | Bind only amount; create server-owned record; validate finite positive values; tolerate invalid legacy totals safely. |
| Medium | `controller/AuthController.java`, `config/CorsConfig.java`, auth DTOs | Duplicate CORS has misspelled port; invalid credentials return 400; unbounded passwords may fail BCrypt. | One configurable CORS policy; 401 credentials response; bounded input and safe errors. |
| Medium | `service/{DailyTargetService,DashboardService}.java`, meal/water repositories | Incomplete profile can retain stale calculated targets; invalid legacy height can produce nonfinite BMI; date range boundaries need explicit treatment. | Clear unsupported targets, validate calculation inputs, use inclusive start/exclusive end dates. Label targets as estimates. |
| Medium | `backend/mvnw.cmd`, test setup | Wrapper indexes a null PowerShell Target array and fails before Maven; baseline test relies on local service configuration. | Null-safe wrapper path check and isolated automated tests with explicit test settings. |

## Phase 2 — Important

| Severity | File / affected files | Problem and why it matters | Recommended fix |
|---|---|---|---|
| High | `service/{UsdaFoodDataProvider,OpenFoodFactsProvider,GroqService,NutritionLookupService}.java` | Raw exception messages/bodies can disclose credential-bearing URLs or user content; provider calls lack bounded timeouts. | Log provider/status/error class only; configure finite connect/read timeout; cap retry waits. |
| High | `service/OpenFoodFactsProvider.java` | OFF `_100g` fields can describe 100 mL; gram-based logging assumes density. Some returned records lack retrievable IDs/nutrients. | Accept only supported mass-basis products for this grams-only interface, preserve unknown values and require usable IDs. |
| High | `service/NutritionMealService.java`, meal DTO | Insufficient quantity/meal-type validation and weak returned-source checks can persist incorrect values. | Validate finite grams and meal type; re-fetch exact source/ID and verify identity/basis before scaling. Preserve existing server refetch. |
| Medium | `service/UsdaFoodDataProvider.java`, nutrition DTO | No nonfinite/negative nutrient filtering or ID deduplication; legacy nutrient numbers differ from modern IDs. | Validate units and values; support actual response schemas explicitly; preserve source ID/data type. |
| High | `service/GroqService.java` | No retrieved nutrition/KG context; numeric rules are prompt-only, raw model output is returned. | Deterministic backend facts for explicit selected foods, clear source metadata, stronger prompt and conservative output guard; disclose residual semantic limitations. |
| High | `service/ProfileExtractionService.java` | Substring heuristics infer gender from “many”, age from “I am 70 kg”, activity from “every day”; unrelated numeric answers corrupt profiles. | Require explicit self-descriptions or bounded standalone onboarding answers and correct units. |
| Medium | `service/{GroqService,ChatMemory}.java`, chat repository/model | Why-followups regenerate explanations; all history fetched before slicing; onboarding counters are unused. | Reuse stored explanation for a clear followup, bounded history query, remove proven-unused state. |
| Medium | `model/FoodNode.java`, KG controller/repository | Graph identifies food only by name and has no provenance, factual relationships, importer or RAG retrieval. | Return honest unverified legacy metadata; do not invent relationships or source verification. Document missing KG-RAG architecture. |
| High residual | Chat/profile | Allergies/restrictions are not persisted as structured constraints; short history and prompts cannot ensure allergen safety. | Do not claim allergy-safe personalization; ask for current restrictions and document limitation. |
| Medium | Frontend Dashboard/Login/Register/Chat/Profile | API base inconsistent; malformed storage can crash pages; failed dashboard stays Loading; missing submission locks; search races; malformed JSON shapes. | Small shared API/session utility, correct hooks, errors/retry, submission guards, cancel stale searches. |
| Medium | Frontend Dashboard/Profile | USDA/OFF indistinguishable; missing nutrition shown as zero; profile blank/null looks like clearing but PATCH ignores null. | Show source/type/ID and unknown values; explain patch behavior and estimate status. |

## Phase 3 — Cleanup

| Severity | File / affected files | Problem and why it matters | Recommended fix / deletion evidence |
|---|---|---|---|
| Low | `frontend/src/App.css` | Unimported starter styles. | Remove only after confirming no imports/references across frontend. |
| Low | `frontend/src/Dashboard.jsx` | `fatPct` declared but unused (also lint finding). | Remove unused local. |
| Low | `service/MealLogService.java` | `addMeal` has no callers; old arbitrary-entity persistence path. | Remove method after repository-wide reference trace. |
| Low | `model/ChatOnboardingState.java` | Counter unused and lastAskedField write-only. | Remove fields/accessors and corresponding writes. |
| Low | Page CSS, `frontend/src/index.css` | Repeated box-sizing resets; background URL names nonexistent asset. | Keep global reset, remove redundant page resets, fix or remove broken background rule in the UI group. |

## Phase 4 — UI

| Severity | File / affected files | Problem and why it matters | Recommended fix |
|---|---|---|---|
| Medium | Dashboard/Chat/Profile JSX and CSS | Different sidebars, missing profile/logout links, navigation disappears on mobile. | One compact shared navigation with active state and visible mobile links. |
| Medium | Dashboard/Chat/Home CSS | Fixed hero/modal sizing, grid minimums, 100vh chat, nonwrapping actions can overflow. | Responsive minmax grids, bounded scrollable modal, dynamic viewport height, wrapping actions and scaled images. |
| Low | Global/page/auth CSS | Inconsistent palette, typography, radius, tiny labels and dark navigation. | Reuse cream/olive/sage tokens, readable type and restrained cards/buttons. |

## Frontend API contracts (baseline)

All baseline calls matched their controllers; several shared an insecure identity contract rather than a stale URL.

| Frontend call | Backend endpoint | Method | Request body | Authentication at baseline | Status / intended correction |
|---|---|---|---|---|---|
| Login / register auto-login | `/api/auth/login` | POST | username, password | Public | Matched |
| Register | `/api/auth/register` | POST | name, username, password | Public | Matched |
| Dashboard | `/api/dashboard/{userId}` | GET | None | Bearer ignored | Change to `/api/dashboard`, JWT owner |
| Dashboard water | `/api/water` | POST | userId, amountLiters | Bearer ignored | Remove userId, JWT owner |
| Dashboard search | `/api/nutrition/search?query=…` | GET | None | Bearer ignored | Require JWT |
| Dashboard meal | `/api/nutrition/log-meal` | POST | userId, mealType, source, sourceId, quantityGrams | Bearer ignored | Remove userId, JWT owner |
| Chat | `/api/chat` | POST | conversationId, message | Bearer ignored | Remove conversationId, JWT owner |
| Profile | `/api/profile` | GET | None | JWT validated | Matched; consolidate validation |
| Profile | `/api/profile` | PATCH | age, height, weight, gender, dietType, activityLevel, goal | JWT validated | Matched; enums match; targets not submitted |

## Evidence and verification plan

USDA [API guide](https://fdc.nal.usda.gov/api-guide/) documents `/fdc/v1/foods/search`, `/food/{fdcId}` and API-key use. [Foundation Foods documentation](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/) distinguishes nutrient IDs from legacy nutrient numbers and describes mass-basis values. [Data documentation](https://fdc.nal.usda.gov/data-documentation/) distinguishes USDA-hosted manufacturer label data from laboratory analysis. “Verified” in this application must mean retrievable source provenance, not government certification of a product.

OFF [nutrition schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition/) distinguishes per-100-g/per-100-mL values. Its [search API](https://openfoodfacts.github.io/search-a-licious/users/ref-openapi/) supports the existing search request shape. OFF is a non-government product database, already marked `PRODUCT_DATABASE` and `verified=false`; preserve that distinction.

Baseline: `mvnw.cmd clean test` failed before compilation (null wrapper Target array). `npm run build` failed on permissions clearing existing dist; no frontend PASS claimed. Lint reported unused fatPct and Dashboard/Profile hook dependency warnings. Installed Maven and isolated output directory are being checked.

For each implementation group: compile/test backend and build frontend, resolve failures before proceeding. Add focused regression coverage for identity/roles/expiry, provider mapping/refetch/fallback/unknown data, malformed quantities, profile extraction and numeric chat handling. Check browser behavior at 1920, 1366, 1024, 768 and 390 pixels. Live register/login/database/provider checks depend on available services; distinguish fixture-backed tests from live checks in final results.

## Implementation checkpoints

- Phase 1: fixed wrapper; `mvnw.cmd clean test` passed, followed by the security regression run (7 tests, no failures). Frontend production build passed in an isolated cache directory because existing `dist` artifacts had sandbox permission restrictions.
- Phase 2: backend compiled; one Mockito re-stubbing error in the new fallback test was corrected. `mvnw.cmd test` then passed with 29 tests, no failures/errors, one opt-in live USDA test skipped. Frontend production build passed. Exact-source food answers now bypass generation, and unsupported quantity/source claims in generated prose are screened. A natural-language guard is not a semantic guarantee.
- Browser baseline: nine mocked-API functional checks passed. Measurements at all five requested widths confirmed no document overflow and usable chat inputs/modals, but authenticated navigation disappeared at 768/390 pixels. Logout initially failed a harness route expectation; the actual application correctly redirected to login. The harness expectation was corrected before the final run.

## Deletion ledger

| File | Item | Why safe to remove |
|---|---|---|
| `backend/src/main/java/com/nutriverse/backend/model/ChatOnboardingState.java` | Class, unused counter, write-only lastAskedField | Only ProfileExtractionService used the class; its sole meaningful pending-field state now uses a bounded map directly. Repository-wide reference search confirms no other callers. |
| `backend/src/main/java/com/nutriverse/backend/service/MealLogService.java` | `addMeal` | No controller or service calls it. All current meal writes use NutritionMealService and exact provider re-fetch. |
| `backend/src/main/java/com/nutriverse/backend/service/NutritionProvider.java` | Null-returning default implementation of `findBySourceId` | Both actual providers already implement exact-ID retrieval; making the existing method required prevents silently missing implementations. |
| `backend/src/main/java/com/nutriverse/backend/dto/ChatRequest.java` | `conversationId` | Authenticated JWT owner replaces caller identity; frontend and controller migrated together. |
| `backend/src/main/java/com/nutriverse/backend/dto/NutritionMealRequest.java` | `userId` | Same authenticated-owner migration; no remaining reads/writes of the DTO field. |
| `backend/src/main/java/com/nutriverse/backend/service/JwtService.java` | Hardcoded secret and separately reparsing accessors | All authentication now uses one validated claims parse; old accessors have no callers. Token generation is retained. |
| `backend/src/main/java/com/nutriverse/backend/controller/AuthController.java` | Local CrossOrigin annotation | The shared configurable CORS policy covers auth and private API routes; removed annotation had the wrong port. |
| `package-lock.json` (repository root) | Empty lockfile | No root package.json or root JavaScript package exists; its packages map is empty. The actual frontend lockfile is retained. |
