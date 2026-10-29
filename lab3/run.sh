#!/usr/bin/env bash
set -e
docker compose build
docker compose up -d \
  --scale player=3 \
  --scale session=2 \
  --scale applicant=2 \
  --scale credential=2 \
  --scale records=2
echo "Gateway ready →  http://localhost:8000"
echo "Discovery      →  http://localhost:8090/services"


