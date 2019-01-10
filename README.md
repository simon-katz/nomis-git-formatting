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

Add `<path-to-this-repo>/bin` to your `PATH`.

ln -s <path-to-this-repo> ~/nomis-git-formatting

## Per-Repo Setup

`git config core.hooksPath <path-to-this-repo>/hooks`
