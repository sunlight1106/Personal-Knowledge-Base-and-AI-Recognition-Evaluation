# Interface languages

Chinese is the default. `LanguageToggle` and Settings → Appearance share a reactive preference stored under `pkb:ui-language`. It is a browser preference, independent of account data and theme settings. The English catalog loads only when English is selected and is included in the existing offline asset manifest.

Use `uiText('中文界面文案')` in the render path for application copy, with a corresponding entry in `en.json`. Translate metadata labels when displaying them; keep route IDs, option values, permission codes and request parameters unchanged. Use `uiLocale()` for displayed dates. Do not remount the router or editor on a language change.

Do not translate user-authored notes, code, messages, names, files, model output or learning material. Unknown strings remain unchanged. `uiRoleName` translates only the original names of built-in role codes and preserves custom role names.

Run `node cle/src/tests/run-personal-ui.mjs locale-ui.mjs` from the repository root. The suite checks live switching, persistence, storage failure, rapid changes, navigation coverage, form preservation and unchanged note/code content. It makes no account or model API requests.
