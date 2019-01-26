TODO Write this README

# Motivation

Include:

- This is (initially at least) aimed at Clojure
  - Might be easy to adapt to other languages
- Don't want to argue about formatting
- Different editors and IDEs have different ideas about formatting
- Standard format in Git
  - Explain the required cljfmt setup
- Individual developers can do what they want
  - But only with whitespace at the start of a line
    - But this is enough for Clojure

# Installation

## Global Setup

`brew install clojure` # TODO Make this non-Mac-dependent.
`brew install planck`  # TODO Make this non-Mac-dependent.

Set the environment variable `NOMIS_GIT_FORMATTING_DIR` to this directory, and add the bin directory to your PATH. For example:

```
export NOMIS_GIT_FORMATTING_DIR=<path-to-this-directory>
export PATH="${NOMIS_GIT_FORMATTING_DIR}/bin:${PATH}"
```

## Per-Repo Setup

`git config core.hooksPath ${NOMIS_GIT_FORMATTING_DIR}/hooks`


# Usage

Include:

- Push without hooks:
    git push --no-verify

- Point at nomis-do-to-all-git-repos
  eg
    nomis-do-to-all-git-repos -n git config core.hooksPath
