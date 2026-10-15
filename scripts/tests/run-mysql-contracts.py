#!/usr/bin/env python3
"""Run database contracts against a disposable MySQL; never reads project .env.

Requires Docker, Python 3.10+, and Java 17. Reports remain in target/surefire-reports.
The container has no persistent volume and is removed even if a test fails.
"""
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid


def main():
    root = Path(__file__).resolve().parents[2]
    # Include every environment-gated contract, except the separate Linux restore
    # rehearsal, plus contracts that otherwise fall back to H2.
    classes = set()
    for path in (root / "src/test/java").rglob("*Test.java"):
        source = path.read_text(encoding="utf-8")
        if "EnabledIfEnvironmentVariable" in source and "MYSQL_TEST_URL" in source and path.stem != "MySqlBackupRestoreTest":
            classes.add(path.stem)
    classes.update({
        "BillingConcurrencyIntegrationTest", "AdminMutationConcurrencyIntegrationTest",
        "PersonalPlatformHttpIntegrationTest", "InferenceRecoveryIntegrationTest",
        "PersonalAiPersistenceIntegrationTest",
        "NoteReferenceBatchQueryTest",
    })
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tests", nargs="+", choices=sorted(classes), help="Rerun selected contract classes")
    args = parser.parse_args()
    if args.tests:
        classes = set(args.tests)
    name = "pkb-contracts-" + uuid.uuid4().hex[:12]
    env = os.environ.copy()
    env.update(MYSQL_ROOT_PASSWORD=secrets.token_urlsafe(32), MYSQL_ROOT_HOST="%")

    def docker(*args, check=True):
        return subprocess.run(["docker", *args], env=env, capture_output=True,
                              text=True, check=check, timeout=180)

    started = False
    try:
        docker("run", "--rm", "-d", "--name", name, "--memory=768m", "--pids-limit=256",
               "-e", "MYSQL_ROOT_PASSWORD", "-e", "MYSQL_ROOT_HOST",
               "-p", "127.0.0.1::3306", "mysql:8.4")
        started = True
        print("Waiting for disposable MySQL...", flush=True)
        for _ in range(90):
            probe = docker("exec", name, "sh", "-c",
                           'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"',
                           check=False)
            if probe.returncode == 0:
                break
            time.sleep(2)
        else:
            raise RuntimeError("Disposable MySQL did not become ready")
        port = docker("port", name, "3306/tcp").stdout.strip().rsplit(":", 1)[1]
        env.update(
            MYSQL_TEST_URL=f"jdbc:mysql://127.0.0.1:{port}/?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true",
            MYSQL_TEST_USERNAME="root", MYSQL_TEST_PASSWORD=env["MYSQL_ROOT_PASSWORD"],
        )
        wrapper = ["cmd.exe", "/d", "/c", str(root / "mvnw.cmd")] if os.name == "nt" else [str(root / "mvnw")]
        print(f"Running {len(classes)} database contract classes", flush=True)
        return subprocess.run(wrapper + ["-B", "-Dtest=" + ",".join(sorted(classes)), "test"],
                              cwd=root, env=env).returncode
    finally:
        cleanup = docker("rm", "-f", name, check=False)
        if started and cleanup.returncode != 0:
            raise RuntimeError(f"Could not remove disposable container {name}; check Docker")
        if cleanup.returncode == 0:
            print("Removed disposable MySQL", flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
