"""Single place that finds a JDK on this box and puts javap/java on PATH."""
import os
import shutil
from pathlib import Path

CANDIDATES = [
    Path.home() / "opt" / "jdk-17.0.20.1+1",
    Path.home() / "opt" / "jdk-21.0.13+9",
]


def ensure_java() -> str | None:
    home = os.environ.get("JAVA_HOME")
    if not home or not (Path(home) / "bin").is_dir():
        for c in CANDIDATES:
            if (c / "bin").is_dir():
                home = str(c)
                break
    if not home:
        return None
    os.environ["JAVA_HOME"] = home
    binp = str(Path(home) / "bin")
    if binp not in os.environ.get("PATH", "").split(os.pathsep):
        os.environ["PATH"] = binp + os.pathsep + os.environ.get("PATH", "")
    return home
