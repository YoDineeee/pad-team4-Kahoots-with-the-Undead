"""Small helpers shared by every endpoint of this service.

Nothing here is Base-specific: auth, error responses, ids and timestamps.
(The Crafting Service has its own copy on purpose — microservices should
not share code at runtime.)
"""
import logging
import uuid
from datetime import datetime, timezone
from functools import wraps

from flask import g, jsonify, request

log = logging.getLogger("base-service")

# ---------------------------------------------------------------------------
# Authentication (fake, for the lab)
# ---------------------------------------------------------------------------
# The spec says every call carries `Authorization: Bearer <jwt>`.
# Verifying real JWTs (signature, expiry, claims) is out of scope here, so a
# few demo tokens are mapped to roles instead. Any other token = a player.
#
#   Authorization: Bearer service-token     -> role "service"   (Game Service)
#   Authorization: Bearer moderator-token   -> role "moderator"
#   Authorization: Bearer <anything else>   -> role "player"
#
# Switching to real JWTs later only means changing `role_from_token`.
DEMO_TOKENS = {
    "service-token": "service",
    "moderator-token": "moderator",
}


def role_from_token(token):
    return DEMO_TOKENS.get(token, "player")


def require_auth(*allowed_roles):
    """Decorator: rejects requests without a Bearer token.

    @require_auth()                -> any authenticated caller
    @require_auth("service")       -> only other microservices (<service_jwt>)
    @require_auth("moderator")     -> only moderators
    """
    def decorator(view):
        @wraps(view)
        def wrapper(*args, **kwargs):
            header = request.headers.get("Authorization", "")
            token = header.removeprefix("Bearer ").strip()
            if not header.startswith("Bearer ") or not token:
                return error("Missing or invalid Authorization header.", 401)

            g.role = role_from_token(token)
            log.debug("Caller role: %s", g.role)
            if allowed_roles and g.role not in allowed_roles:
                return error(f"Role '{g.role}' is not allowed to call this endpoint.", 403)

            return view(*args, **kwargs)
        return wrapper
    return decorator


# ---------------------------------------------------------------------------
# Request / response helpers
# ---------------------------------------------------------------------------
def error(message, status, **extra):
    """Every error in the spec looks like {"error": "..."} (+ optional extra keys)."""
    log.warning("Request rejected: %s %s%s", status, message, f" {extra}" if extra else "")
    return jsonify({"error": message, **extra}), status


def read_json():
    """Request body as a dict ({} if the body is missing or not JSON)."""
    body = request.get_json(silent=True)
    return body if isinstance(body, dict) else {}


def missing_fields(body, *names):
    """Names of required fields that are absent or empty in the body."""
    return [name for name in names if body.get(name) in (None, "")]


def new_id(prefix):
    """E.g. new_id("base") -> "base-3f2a9c1e"."""
    return f"{prefix}-{uuid.uuid4().hex[:8]}"


def now_iso():
    """Current UTC time in the spec's format: 2026-09-10T14:00:00Z."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    