"""Export current source files, excluding ignored local build and runtime data."""
import subprocess
import zipfile
from pathlib import Path

project = Path(__file__).resolve().parents[1]


def package_source():
    files = subprocess.check_output(
        ["git", "-c", "safe.directory=" + project.as_posix(), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=project).decode("utf-8").split("\0")
    output = project / "target/3DGames-source.zip"
    output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in files:
            if name and (project / name).is_file():
                archive.write(project / name, name)
    return output


if __name__ == "__main__":
    print(package_source())
