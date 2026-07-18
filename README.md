# jadx-plugin-method-role-tagger

A [jadx](https://github.com/skylot/jadx) plugin that tags every decompiled method with a structural **role** (override/lifecycle callback, synthetic/bridge, constructor, or plain method) and an IR-based **complexity** score (basic block count), writing the result as a JSON sidecar file next to the decompiled source.

## Problem

Downstream diff/changelog tooling needs to rank which hunks in a large "hub" file are most likely to represent real, significant behavior (as opposed to boilerplate getters/setters or synthetic glue code). Raw hunk size is a weak proxy — a large getter is not more significant than a small but structurally complex business-logic method.

jadx already computes the exact signals needed for this internally (override detection via `OverrideMethodVisitor`, access flags, basic-block-based control flow) — this plugin just exposes them.

## Output

Writes `method_roles.json` into the output sources directory (`-d`/`-ds`), one entry per decompiled method:

```json
[
  { "class": "com.example.MainActivity", "method": "onCreate", "signature": "onCreate(Landroid/os/Bundle;)V", "role": "override", "complexity": 12 },
  { "class": "com.example.MainActivity", "method": "getTitle", "signature": "getTitle()Ljava/lang/String;", "role": "method", "complexity": 2 }
]
```

- `role`: `override` (implements/overrides a supertype method — lifecycle callbacks, listener implementations, etc.), `synthetic` (compiler-generated bridge/synthetic method), `constructor`, or `method` (plain method).
- `complexity`: number of basic blocks in the method's control-flow graph, or `-1` if unavailable (e.g. abstract/native methods).

## Usage

```bash
java -cp "jadx-all.jar;jadx-plugin-method-role-tagger.jar" jadx.cli.JadxCLI input.apk -d output
```

(use `:` instead of `;` on Linux/macOS)
