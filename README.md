# NutriVerse

React/Vite frontend and Java 21/Spring Boot backend for nutrition tracking and explainable food guidance. MongoDB stores accounts, profiles, meals and chat history. Neo4j currently exposes a read-only food-node list; graph-backed recommendation retrieval is not implemented.

## Run locally

1. Install Java 21, Maven (or use the included wrapper), Node.js compatible with the installed Vite release, MongoDB and Neo4j.
2. Copy `backend/.env.example` to `backend/.env`. Set provider credentials and a randomly generated `JWT_SECRET` of at least 32 bytes. Never commit `.env`. Replacing the JWT secret invalidates existing tokens.
3. Configure `MONGODB_URI`, `MONGODB_DATABASE`, `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` as needed. The URI defaults target local databases. `CORS_ALLOWED_ORIGINS` is a comma-separated allowlist; the default is `http://localhost:5173`.
4. From `backend`, run `./mvnw.cmd spring-boot:run` on Windows (or `./mvnw spring-boot:run` elsewhere).
5. Copy `frontend/.env.example` to `frontend/.env` if overriding the backend origin. `VITE_API_URL` is the origin, without `/api`; Vite reads it at startup/build time. Never put API keys in `VITE_` variables.
6. From `frontend`, run `npm ci` and `npm run dev`.

## API and data rules

Login and registration are public. All other `/api` routes require `Authorization: Bearer <token>`. Identity comes from validated JWT claims, not `userId` or `conversationId` in requests. Current-user routes include `/api/dashboard`, `/api/profile`, `/api/meals`, `/api/meals/today`, `/api/water`, `/api/water/today` and `/api/water/today/total`. `/api/kg/foods` requires an ADMIN token; registration always creates USER accounts.

Food search uses USDA FoodData Central first, then Open Food Facts when USDA has no usable result or is unavailable. Every selectable food retains its source and exact ID. Meal logging sends only `mealType`, `source`, `sourceId` and `quantityGrams`; the backend fetches that exact record again and scales its mass-based values. Missing nutrients remain unknown. OFF products with unknown or volume-based units are not eligible for the grams-only logger.

USDA `verified=true` means the values were retrieved from a USDA record with a retained FDC ID. It does not mean USDA certified a branded product or independently measured every manufacturer label. OFF is a non-government product database and always remains `verified=false`. Old records without provenance must be treated as source not verified.

Chat accepts `{message}` or `{message, source, sourceId}` for a selected food. Exact selected-food facts are assembled by backend code after source retrieval. Free-form model explanations are constrained and screened for unsupported quantities; this is not a proof of semantic correctness. No authoritative dietary-guideline documents or graph evidence are currently retrieved. Allergies are not persistent structured profile fields and must not be represented as reliably enforced.

Profile PATCH accepts age, height, weight, gender, dietType, activityLevel and goal; omitted/null values leave fields unchanged. Targets are backend-calculated estimates from the existing formula, not government-verified food facts or clinical prescriptions. Incomplete/unsupported profiles, including children, do not receive calculated targets. See [NIDDK's adult-use scope](https://www.niddk.nih.gov/health-information/weight-management/body-weight-planner) for why adult weight-planning assumptions should not be applied to children. This application does not implement NIDDK's model.

## Verify

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd clean package
cd ../frontend
npm run build
npm run lint
```

Automated regression tests use synthetic records and mock provider responses. They do not certify external service availability or historical database contents. The opt-in live USDA test requires a real key and reachable USDA service; see [AUDIT.md](AUDIT.md) for commands, actual results, the contract table, deletion rationale and remaining risks.
