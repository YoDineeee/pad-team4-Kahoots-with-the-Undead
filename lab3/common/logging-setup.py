"""Grade 3: explicit, uniform logs. Every line carries the service + instance id."""
import logging
import os
import sys


def setup_logging(service: str, instance: str) -> None:
    fmt = f"%(asctime)s %(levelname)-7s [{service}/{instance}] %(name)s: %(message)s"
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format=fmt, stream=sys.stdout, force=True)
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)  # we log requests ourselves

    