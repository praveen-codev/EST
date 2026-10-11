#!/usr/bin/env bash
# Generates the secrets the qa/prod profiles require and prints them in .env format.
# Nothing is written to disk; redirect to .env yourself (which is gitignored).
set -euo pipefail
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/jwt.pem" 2>/dev/null
openssl pkey -in "$tmp/jwt.pem" -pubout -out "$tmp/jwt.pub" 2>/dev/null
one_line() { awk 'NF {printf "%s\\n", $0}' "$1"; }
echo "EST_JWT_PRIVATE_KEY=\"$(one_line "$tmp/jwt.pem")\""
echo "EST_JWT_PUBLIC_KEY=\"$(one_line "$tmp/jwt.pub")\""
echo "EST_CRYPTO_KEY=$(openssl rand -base64 32)"
echo "POSTGRES_PASSWORD=$(openssl rand -hex 16)"
