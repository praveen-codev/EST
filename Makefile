# Enablement Session Tracker — monorepo entry points.
# web/      Next.js app (pnpm)        backend/  Spring Boot API (Gradle)      infra/  Docker compose
SHELL := /bin/bash
SDKMAN := source $$HOME/.sdkman/bin/sdkman-init.sh >/dev/null 2>&1 && sdk env >/dev/null 2>&1 || true

.PHONY: help web api verify verify-web verify-api up-local up-qa down logs e2e

help:
	@echo "make web         start the web app on :3100 (expects the API on :8080 or mock mode)"
	@echo "make api         start the API with the local profile (H2, seeded, mock Google)"
	@echo "make verify      web lint/typecheck/test/build + backend gradle check"
	@echo "make up-local    docker compose: API (H2) + web"
	@echo "make up-qa       docker compose: API (qa) + PostgreSQL"
	@echo "make e2e         Playwright against the web dev server"

web:
	cd web && pnpm dev --port 3100

api:
	cd backend && $(SDKMAN); ./gradlew bootRun --args='--spring.profiles.active=local'

verify: verify-web verify-api

verify-web:
	cd web && pnpm lint && pnpm typecheck && pnpm test && pnpm build

verify-api:
	cd backend && $(SDKMAN); ./gradlew check --no-daemon

up-local:
	docker compose -f infra/compose.yaml --profile local up --build

up-qa:
	docker compose -f infra/compose.yaml --profile qa up --build

down:
	docker compose -f infra/compose.yaml --profile local --profile qa down

logs:
	docker compose -f infra/compose.yaml logs -f api

e2e:
	cd web && pnpm e2e
