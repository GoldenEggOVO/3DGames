import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from shutil import copyfile


SCRIPT = Path(__file__).resolve().parents[1] / "build.py"


class BuildCommandLineTest(unittest.TestCase):
    def run_script(self, option):
        with tempfile.TemporaryDirectory(dir=Path.cwd()) as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            script = root / "tools" / "build.py"
            copyfile(SCRIPT, script)
            return subprocess.run([sys.executable, str(script), option], capture_output=True, text=True)

    def test_help_does_not_start_a_build(self):
        result = self.run_script("--help")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("usage:", result.stdout.lower())
        self.assertNotIn("Cleaning previous build", result.stdout)

    def test_unknown_option_is_rejected_before_building(self):
        result = self.run_script("--not-a-build-option")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("Cleaning previous build", result.stdout)


if __name__ == "__main__":
    unittest.main()
