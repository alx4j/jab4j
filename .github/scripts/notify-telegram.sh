#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${TELEGRAM_CHAT_ID:-}" ]]; then
  echo "Telegram notification skipped because TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not configured."
  exit 0
fi

if [[ -z "${TELEGRAM_MESSAGE:-}" ]]; then
  echo "Telegram notification skipped because TELEGRAM_MESSAGE is empty."
  exit 0
fi

curl -fsS \
  --retry 3 \
  --retry-delay 2 \
  --connect-timeout 10 \
  --max-time 30 \
  -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
  --data-urlencode "text=${TELEGRAM_MESSAGE}" \
  --data-urlencode "disable_web_page_preview=true" \
  >/dev/null
