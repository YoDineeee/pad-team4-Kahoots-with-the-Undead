#!/usr/bin/env python3
"""
Build & run script for the Gateway, the Service Discovery and all the services behind them:
player, game, zombie, resource (Java) and base, crafting (Python).
Works on Windows, macOS and Linux (only needs Python 3.8+).

  python run.py docker              # build all images and start them with Docker Compose (recommended)
  python run.py stop                # stop the containers
  python run.py scale zombie-service 2
                                    # change the number of running instances (lab 3), no restart needed
  python run.py local               # run gateway + player + game without Docker (needs JDK 21 + Maven + Node 22)
  python run.py test                # run the unit tests of all services (needs JDK 21 + Maven + Node 22 + Python)
  python run.py push --user <dockerhub_user> [--version 1.0.0]
                                    # build, tag as <user>/<service>:<version> and push to Docker Hub
"""
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAVA_SERVICES = ["player-service", "game-service", "zombie-service", "resource-service"]
PYTHON_SERVICES = ["base-service", "crafting-service"]
NODE_SERVICES = ["gateway", "service-discovery"]
SERVICES = NODE_SERVICES + JAVA_SERVICES + PYTHON_SERVICES
# Services without their own version in .env use VERSION / --version
SERVICE_VERSIONS = {"zombie-service": "1.1.0", "resource-service": "1.1.0"}
# Only these run in "local" mode: zombie/resource need PostgreSQL -> use "docker"
LOCAL_JAVA_SERVICES = ["player-service", "game-service"]
# On Windows, mvn and npm are .cmd scripts and must be started through the shell
WINDOWS_SCRIPTS = {"mvn", "npm"}
DEFAULT_VERSION = "1.0.0"


def run(cmd, cwd=ROOT, env=None):
    """Print and run a command; stop the script if it fails."""
    print(f"\n$ {' '.join(cmd)}   (in {cwd.name})")
    result = subprocess.run(cmd, cwd=cwd, env=env, shell=(os.name == "nt" and cmd[0] in WINDOWS_SCRIPTS))
    if result.returncode != 0:
        sys.exit(f"Command failed with exit code {result.returncode}")


def require(tool):
    if shutil.which(tool) is None:
        sys.exit(f"'{tool}' was not found on PATH. Please install it first.")


def ensure_env_file():
    """docker compose reads secrets from .env; create it from the example on first run."""
    env_file, example = ROOT / ".env", ROOT / ".env.example"
    if not env_file.exists():
        shutil.copy(example, env_file)
        print("Created .env from .env.example - change JWT_SECRET before using this anywhere real.")


def cmd_docker(_args):
    require("docker")
    ensure_env_file()
    run(["docker", "compose", "up", "--build", "-d"])
    print("\nGateway (single entry point): http://localhost:8080   (the Java services need ~30 s to start)")
    print("Health of all services:       http://localhost:8080/health")
    print("Registered instances (lab 3): http://localhost:8761/services")
    print("Load balancer + breaker:      http://localhost:8080/status")
    print("Logs: docker compose logs -f      Stop: python run.py stop")


def cmd_stop(_args):
    require("docker")
    run(["docker", "compose", "down"])


def cmd_scale(args):
    """Lab 3: start/stop instances of one service while everything keeps running.
    New instances register with the Service Discovery; the gateway picks them up within seconds."""
    require("docker")
    run(["docker", "compose", "up", "-d", "--no-recreate", "--scale", f"{args.service}={args.instances}"])
    print(f"\n{args.service}: {args.instances} instance(s). Watch them appear: http://localhost:8761/services")


def cmd_local(_args):
    require("mvn")
    require("npm")
    run(["npm", "install"], cwd=ROOT / "gateway")
    processes = []
    try:
        print("Note: local mode starts gateway, player and game only. For the full system use: python run.py docker")
        for service in LOCAL_JAVA_SERVICES:
            print(f"Starting {service} ...")
            processes.append(subprocess.Popen(["mvn", "-q", "spring-boot:run"], cwd=ROOT / service,
                                              shell=(os.name == "nt")))
        print("Starting gateway ...")
        processes.append(subprocess.Popen(["npm", "start"], cwd=ROOT / "gateway", shell=(os.name == "nt")))
        print("\nStarting: gateway 8080 (use this one), player 8081, game 8082. Press Ctrl+C to stop.")
        for p in processes:
            p.wait()
    except KeyboardInterrupt:
        print("\nStopping ...")
    finally:
        for p in processes:
            p.terminate()


def cmd_test(_args):
    require("mvn")
    require("npm")
    for service in JAVA_SERVICES:
        run(["mvn", "-q", "test"], cwd=ROOT / service)
    for service in NODE_SERVICES:
        run(["npm", "install"], cwd=ROOT / service)
        run(["npm", "test"], cwd=ROOT / service)
    for service in PYTHON_SERVICES:
        # each Python service has its own run.py that creates a venv and runs pytest
        run([sys.executable, "run.py", "test"], cwd=ROOT / service)
    print("\nAll tests passed.")


def cmd_push(args):
    require("docker")
    for service in SERVICES:
        version = SERVICE_VERSIONS.get(service, args.version)
        versioned = f"{args.user}/{service}:{version}"
        latest = f"{args.user}/{service}:latest"
        run(["docker", "build", "-t", versioned, "-t", latest, "."], cwd=ROOT / service)
        run(["docker", "push", versioned])
        run(["docker", "push", latest])
    print(f"\nPushed {len(SERVICES)} images. Remember: the repositories on Docker Hub must be PUBLIC.")


def main():
    parser = argparse.ArgumentParser(description="Build and run the PAD services")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("docker", help="build + run with Docker Compose").set_defaults(func=cmd_docker)
    sub.add_parser("stop", help="stop Docker Compose").set_defaults(func=cmd_stop)
    scale = sub.add_parser("scale", help="change the number of instances of a service (lab 3)")
    scale.add_argument("service", help="e.g. zombie-service or resource-service")
    scale.add_argument("instances", type=int, help="how many instances should run")
    scale.set_defaults(func=cmd_scale)
    sub.add_parser("local", help="run with Maven (no Docker)").set_defaults(func=cmd_local)
    sub.add_parser("test", help="run unit tests").set_defaults(func=cmd_test)
    push = sub.add_parser("push", help="build, tag and push images to Docker Hub")
    push.add_argument("--user", required=True, help="your Docker Hub username")
    push.add_argument("--version", default=DEFAULT_VERSION, help=f"image tag (default {DEFAULT_VERSION})")
    push.set_defaults(func=cmd_push)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

    