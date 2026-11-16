import json
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class BuildTest(unittest.TestCase):
    def setUp(self):
        directory = ROOT / "target/build-tests"
        directory.mkdir(parents=True, exist_ok=True)
        workspace = tempfile.TemporaryDirectory(dir=directory)
        self.addCleanup(workspace.cleanup)
        self.workspace = Path(workspace.name)
        self.project = self.workspace / "project with spaces"
        self.project.mkdir()
        for name in ("tools/build.py", "tools/package_resource_pack.py"):
            if (ROOT / name).is_file():
                target = self.project / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / name, target)
        self.write("pom.xml", '<project><version>1.2.3</version></project>')
        self.write("resource-pack/pack.mcmeta", {"pack": {"pack_format": 88, "description": "Fixture"}})
        self.write("resource-pack/assets/3dgames/items/shared/tile.json", {
            "model": {"type": "minecraft:model", "model": "3dgames:item/shared/tile"}})
        self.write("resource-pack/assets/3dgames/models/item/shared/tile.json", {"textures": {}, "elements": []})
        self.write("resource-pack/craftengine/resources/3dgames/configuration/tabletop/items.yml",
                   "items:\n  3dgames:tile:\n    material: paper\n    item_model: 3dgames:shared/tile\n")
        (self.project / "src/main/resources").mkdir(parents=True)
        jdk = self.workspace / "jdk with spaces"
        (jdk / "bin").mkdir(parents=True)
        (jdk / "bin" / ("javac.exe" if os.name == "nt" else "javac")).touch()
        (jdk / "release").write_text('JAVA_VERSION="25.0.2"\n')
        self.env = dict(os.environ, JAVA_HOME=str(jdk))
        maven = self.workspace / ".tools/apache-maven-3.9.11/bin"
        maven.mkdir(parents=True)
        (self.workspace / ".tools/m2").mkdir()
        if os.name == "nt":
            (maven / "mvn.cmd").write_bytes((
                f'@echo off\r\n"{sys.executable}" -X utf8 "%~dp0maven_stub.py" %*\r\n'
                'exit /b %errorlevel%\r\n').encode())
        else:
            executable = maven / "mvn"
            executable.write_text(f'#!/bin/sh\nexec {shlex.quote(sys.executable)} -X utf8 '
                                  f'{shlex.quote(str(maven / "maven_stub.py"))} "$@"\n')
            executable.chmod(0o755)
        (maven / "maven_stub.py").write_text(
            'import json, os, shutil, sys, zipfile\n'
            'from pathlib import Path\n'
            'Path("maven-called.json").write_text(json.dumps(sys.argv[1:]))\n'
            'mode = os.environ.get("TABLETOP_TEST_MAVEN", "success")\n'
            'if mode == "fail": raise SystemExit(7)\n'
            'if "clean" in sys.argv:\n'
            '    if Path("target").exists(): shutil.rmtree("target")\n'
            '    raise SystemExit(0)\n'
            'checksum = Path("src/main/resources/resource-pack.sha1").read_text()\n'
            'if mode == "stale": checksum = "0" * 40\n'
            'with zipfile.ZipFile("target/3DGames-1.2.3.jar", "w") as jar:\n'
            '    jar.writestr("resource-pack.sha1", checksum)\n'
            '    pack = Path("target/3DGames-resource-pack.zip").read_bytes()\n'
            '    jar.writestr("3DGames-resource-pack.zip", b"broken" if mode == "broken-bundle" else pack)\n'
            '    for path in Path("target/classes").rglob("*.class"):\n'
            '        jar.write(path, path.relative_to("target/classes").as_posix())\n', encoding="utf-8")

    def write(self, name, value):
        path = self.project / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value if isinstance(value, str) else json.dumps(value), encoding="utf-8")

    def build(self):
        command = [sys.executable, "-X", "utf8", str(self.project / "tools/build.py")]
        return subprocess.run(command, input="\n", cwd=self.workspace, env=self.env,
                              capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)

    def test_success_builds_matching_files_from_a_different_working_directory(self):
        result = self.build()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        manifest = json.loads((self.project / "target/resource-pack-manifest.json").read_text())
        with zipfile.ZipFile(self.project / "target/3DGames-1.2.3.jar") as jar:
            self.assertEqual(manifest["sha1"], jar.read("resource-pack.sha1").decode().strip())
        arguments = json.loads((self.project / "maven-called.json").read_text())
        self.assertIn("-o", arguments)
        self.assertIn("package", arguments)
        self.assertIn("Build complete", result.stdout)
        self.assertIn("3DGames-1.2.3.jar", result.stdout)

    def test_build_does_not_require_an_initialized_server(self):
        self.env.pop("PAPER_SERVER_DIR", None)
        result = self.build()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Build complete", result.stdout)

    def test_deleted_source_classes_cannot_survive_in_the_next_jar(self):
        removed = "removed/RetiredMigration.class"
        self.write("target/classes/" + removed, "stale compiled class")
        result = self.build()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        with zipfile.ZipFile(self.project / "target/3DGames-1.2.3.jar") as jar:
            self.assertNotIn(removed, jar.namelist())

    def test_invalid_resource_pack_stops_before_compilation(self):
        self.write("resource-pack/assets/3dgames/textures/broken.png", "Not a PNG")
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid PNG", result.stderr)
        arguments = json.loads((self.project / "maven-called.json").read_text())
        self.assertIn("clean", arguments)
        self.assertNotIn("package", arguments)
        self.assertNotIn("Build complete", result.stdout)

    def test_maven_failure_is_not_reported_as_a_success(self):
        self.env["TABLETOP_TEST_MAVEN"] = "fail"
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((self.project / "maven-called.json").is_file())
        self.assertNotIn("Build complete", result.stdout)

    def test_stale_plugin_checksum_is_rejected(self):
        self.env["TABLETOP_TEST_MAVEN"] = "stale"
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum", result.stderr)
        self.assertNotIn("Build complete", result.stdout)

    def test_mismatched_bundled_resource_pack_is_rejected(self):
        self.env["TABLETOP_TEST_MAVEN"] = "broken-bundle"
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("bundled", result.stderr.lower())
        self.assertNotIn("Build complete", result.stdout)

class SourcePackageTest(unittest.TestCase):
    def test_export_includes_new_packages_and_excludes_deleted_and_ignored_files(self):
        with tempfile.TemporaryDirectory(dir=ROOT / "target") as directory:
            project = Path(directory)
            subprocess.run(["git", "init", "-q", str(project)], check=True)
            (project / ".gitignore").write_text("target/\n")
            (project / "old.java").write_text("old")
            subprocess.run(["git", "-c", "safe.directory=" + project.as_posix(), "add", "."], cwd=project, check=True)
            (project / "old.java").unlink()
            (project / "Core.java").write_text("new shared source")
            (project / "target").mkdir()
            (project / "target/private.txt").write_text("ignored")
            spec = importlib.util.spec_from_file_location("source_package", ROOT / "tools/package_source.py")
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            module.project = project
            with zipfile.ZipFile(module.package_source()) as archive:
                self.assertEqual({".gitignore", "Core.java"}, set(archive.namelist()))
                self.assertEqual(b"new shared source", archive.read("Core.java"))


if __name__ == "__main__":
    unittest.main()
