#!/usr/bin/env python3
"""Build and run the service with one command (works on Windows, macOS, Linux).

    python run.py            install dependencies and start the service
    python run.py test       install dependencies and run the tests
    python run.py docker     build the Docker image and run it in a container

The first run creates a virtual environment in .venv/ so nothing is
installed globally on your machine.
"""
import subprocess
import sys
import venv
from pathlib import Path

# --- The only lines that differ between the two services -------------------
SERVICE_NAME = "base-service"
PORT = 5007
VERSION = "1.0.0"
# ---------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parent
VENV_DIR = ROOT / ".venv"
IMAGE = f"{SERVICE_NAME}:{VERSION}"


def venv_python():
    """Path of the Python interpreter inside .venv (differs on Windows)."""
    if sys.platform == "win32":
        return VENV_DIR / "Scripts" / "python.exe"
    return VENV_DIR / "bin" / "python"


def run(command):
    """Print a command, then run it. Stops the script if the command fails."""
    print("$", " ".join(str(part) for part in command))
    subprocess.run(command, cwd=ROOT, check=True)


def install():
    """Build step: create the virtual environment and install requirements."""
    if not VENV_DIR.exists():
        print(f"Creating virtual environment in {VENV_DIR.name}/ ...")
        venv.create(VENV_DIR, with_pip=True)
    run([venv_python(), "-m", "pip", "install", "--quiet", "-r", "requirements.txt"])


def start():
    install()
    print(f"\n{SERVICE_NAME} running on http://localhost:{PORT}  (Ctrl+C to stop)\n")
    run([venv_python(), "app.py"])


def test():
    install()
    run([venv_python(), "-m", "pytest", "-v"])


def docker():
    run(["docker", "build", "-t", IMAGE, "."])
    print(f"\n{IMAGE} running on http://localhost:{PORT}  (Ctrl+C to stop)\n")
    run(["docker", "run", "--rm", "-p", f"{PORT}:{PORT}", IMAGE])


COMMANDS = {"start": start, "test": test, "docker": docker}

if __name__ == "__main__":
    name = sys.argv[1] if len(sys.argv) > 1 else "start"
    if name not in COMMANDS:
        print(__doc__)
        sys.exit(1)
    try:
        COMMANDS[name]()
    except KeyboardInterrupt:
        print("\nStopped.")
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
    except FileNotFoundError as exc:
        sys.exit(f"Command not found: {exc.filename}. Is it installed and on your PATH?")


        