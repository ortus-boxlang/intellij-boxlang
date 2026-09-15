#!/usr/bin/env python3
"""Probe a locally installed bx-lsp over LSP using isolated, synthetic CFML files.

Usage: python3 scripts/audit-cfml-diagnostics.py --runtime-jar /path/boxlang.jar
       --modules-directory /path/modules --output /path/results.json
No application source or project configuration is read by the server.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import shutil
import subprocess
import tempfile
import time

CASES = [
    ("valid-template.cfm", '<cfoutput>#ucase("ok")#</cfoutput>', False),
    ("valid-script.cfm", '<cfscript>value = len("ok");</cfscript>', False),
    ("valid-component.cfc", 'component { public string function greet(required string name) { return "Hi " & arguments.name; } }', False),
    ("valid-tag-component.cfc", '<cfcomponent><cffunction name="greet" returntype="string"><cfargument name="name" type="string" required="true"><cfreturn arguments.name></cffunction></cfcomponent>', False),
    ("valid-comments.cfm", '<!--- <cfif> ([) ---><cfset value = "([)">', False),
    ("empty.cfm", "", False),
    ("whitespace.cfm", "  \n\t", False),
    ("comment-only.cfm", "<!--- <cfif true> --->", False),
    ("nested-comment.cfm", "<!--- outer <!--- <cfif true> ---> end --->", False),
    ("trailing-comment.cfm", "<cfset value = 1><!--- done --->", False),
    ("text-and-tags.cfm", "hello\n<cfset value = 1>world", False),
    ("unclosed-after-text.cfm", "hello\n<cfif true><cfset value = 1>", True),
    ("unclosed-tag.cfm", '<cfif true><cfset value = 1>', True),
    ("mismatched-tags.cfm", '<cfoutput>hello</cfif>', True),
    ("missing-paren.cfm", '<cfscript>value = len("ok";</cfscript>', True),
    ("missing-brace.cfc", 'component { function greet() { return "ok"; }', True),
    ("missing-expression.cfm", '<cfset value = >', True),
    ("bad-expression.cfm", '<cfscript>value = 1 + ;</cfscript>', True),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-jar", type=Path, required=True)
    parser.add_argument("--modules-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="cfml-diagnostics-") as folder:
        root = Path(folder)
        workspace = root / "workspace"
        workspace.mkdir()
        with socket.socket() as port_socket:
            port_socket.bind(("127.0.0.1", 0))
            port = port_socket.getsockname()[1]
        isolated_modules = root / "modules"
        shutil.copytree(args.modules_directory / "bx-lsp", isolated_modules / "bx-lsp")
        env = dict(os.environ, BOXLANG_HOME=str(root / "home"),
                   BOXLANG_MODULESDIRECTORY=str(isolated_modules))
        with (root / "server.log").open("w+") as log:
            process = subprocess.Popen(["java", "-cp", str(args.runtime_jar.resolve()),
                "ortus.boxlang.runtime.BoxRunner", "module:bx-lsp", "--debug-server-port", str(port)],
                env=env, cwd=workspace, stdout=log, stderr=subprocess.STDOUT)
            connection = None
            try:
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    if process.poll() is not None:
                        log.seek(0)
                        raise RuntimeError("LSP exited: " + log.read()[-4000:])
                    try:
                        connection = socket.create_connection(("127.0.0.1", port), timeout=1)
                        break
                    except OSError:
                        time.sleep(0.1)
                if connection is None:
                    raise TimeoutError("LSP did not start in 30 seconds")
                connection.settimeout(15)
                stream = connection.makefile("rb")
                counter = 0
                pushed = {}

                def send(message):
                    body = json.dumps(dict(jsonrpc="2.0", **message)).encode()
                    connection.sendall(f"Content-Length: {len(body)}\r\n\r\n".encode() + body)

                def call(method, params):
                    nonlocal counter
                    counter += 1
                    request_id = counter
                    send(dict(id=request_id, method=method, params=params))
                    while True:
                        headers = {}
                        while True:
                            line = stream.readline()
                            if not line:
                                raise EOFError("LSP closed the connection")
                            if line == b"\r\n":
                                break
                            key, value = line.decode().split(":", 1)
                            headers[key.lower()] = value.strip()
                        message = json.loads(stream.read(int(headers["content-length"])))
                        if message.get("method") == "textDocument/publishDiagnostics":
                            pushed[message["params"]["uri"]] = message["params"]["diagnostics"]
                        if "method" in message and "id" in message:
                            result = ([{} for _ in message.get("params", {}).get("items", [])]
                                      if message["method"] == "workspace/configuration" else None)
                            send(dict(id=message["id"], result=result))
                        if message.get("id") == request_id and "method" not in message:
                            if "error" in message:
                                raise RuntimeError(message["error"])
                            return message.get("result")

                initialization = call("initialize", dict(processId=os.getpid(), rootUri=workspace.as_uri(),
                    workspaceFolders=[dict(uri=workspace.as_uri(), name="CFML diagnostics audit")],
                    capabilities={"textDocument": {"diagnostic": {}}}))
                send(dict(method="initialized", params={}))
                results = []
                for name, source, expect_error in CASES:
                    path = workspace / name
                    path.write_text(source)
                    uri = path.as_uri()
                    send(dict(method="textDocument/didOpen", params={"textDocument": dict(uri=uri,
                        languageId="cfml", version=1, text=source)}))
                    report = call("textDocument/diagnostic", {"textDocument": {"uri": uri}})
                    diagnostics = report.get("items", []) if report else pushed.get(uri, [])
                    errors = [d for d in diagnostics if d.get("severity") == 1]
                    results.append(dict(file=name, source=source, expect_error=expect_error,
                        errors_observed=bool(errors), matches_expectation=bool(errors) == expect_error,
                        diagnostics=diagnostics))
                    send(dict(method="textDocument/didClose", params={"textDocument": {"uri": uri}}))
                output = dict(runtime=args.runtime_jar.name,
                    runtime_sha256=hashlib.sha256(args.runtime_jar.read_bytes()).hexdigest(),
                    module=json.loads((args.modules_directory / "bx-lsp" / "box.json").read_text())["version"],
                    module_jar_sha256={jar.name: hashlib.sha256(jar.read_bytes()).hexdigest()
                        for jar in sorted((args.modules_directory / "bx-lsp" / "libs").glob("*.jar"))},
                    capabilities=initialization.get("capabilities", {}), cases=results)
                args.output.write_text(json.dumps(output, indent=2) + "\n")
                print(f"{sum(r['matches_expectation'] for r in results)}/{len(results)} cases matched expectations; {args.output}")
            finally:
                if connection:
                    connection.close()
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    main()
