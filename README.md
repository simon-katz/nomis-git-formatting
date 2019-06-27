TODO Finish writing this README.

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

### Install Clojure and Planck

Install Clojure and the Clojure CLI tools. See https://clojure.org/guides/getting_started

Install Planck. See https://github.com/planck-repl/planck

On a Mac, you can do the above with:

```
brew install clojure
brew install planck
```

### Set up this Git Repository

Do the following:

- Clone this repo.
- Set the environment variable `NOMIS_GIT_FORMATTING_DIR` to this repo's directory.
- Add the bin directory to your PATH.

For example:

```
export NOMIS_GIT_FORMATTING_DIR=<path-to-this-repo-directory>
export PATH="${NOMIS_GIT_FORMATTING_DIR}/bin:${PATH}"
```

## Per-Repository Setup

In any Clojure repo that you want to use canonical formatting, do:

```
git config core.hooksPath ${NOMIS_GIT_FORMATTING_DIR}/hooks
```


# Usage

Include:

- Push without hooks:
    `git push --no-verify`

- `nomis-git-reformat-local`

- `nomis-git-reformat-and-push`
