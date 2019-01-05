(ns nomis-git-stuff.common.git
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [nomis-git-stuff.common.utils :as u]))

(defn branch-name []
  (-> (u/bash "git rev-parse --abbrev-ref HEAD")
      u/remove-trailing-newline))

(defn remote-names []
  (-> (u/bash "git remote") ; TODO Is there a better way (eg with rev-parse)?
      u/split-on-newline))

(defn remote-name []
  (let [remotes (remote-names)]
    (assert (= (count remotes) 1))
    (first remotes)))

(defn dirty? []
  (not= (-> (u/bash "git status --porcelain")
            u/remove-trailing-newline)
        ""))

(defn stash-if-dirty-include-untracked [message]
  ;; ## TODO Take a closer look at these stashes.
  ;; ##      - Do they still seem to delete many files?
  ;; ##        - It's with-local-formatting pre-push stashes.
  ;; ##          - They show deleted files in the "Untracked" section.
  (u/bash "git stash push"
          "--quiet"
          "--include-untracked"
          "--message" message))

(defn reset-hard [ref]
  (u/bash "git reset --quiet --hard" ref))

(defn reset-soft [ref]
  (u/bash "git reset --quiet --soft" ref))

(defn checkout-pathspec=dot [tree-ish]
  (u/bash "git checkout --quiet" tree-ish "."))

(defn replace-previous-n-commits-incl-staged [n
                                              commit-sha]
  (println "Committing: Replacing last" n "commits with a single commit")
  (u/bash "git reset --quiet --soft"
          (str "HEAD~" n))
  ;; TODO You added `--allow-empty` here, but the behaviour from
  ;;      the local formatting post-commit hook was probably better before,
  ;;      because you just got an "apply-local-formatting" commit.
  ;;      When you rewrite this in CLJS, look into doing different things
  ;;      depending on whether anything is being committed.
  (u/bash "git commit --quiet --no-verify --allow-empty"
          "-C" commit-sha))

(defn top-stash-name []
  (-> (u/bash "git stash list --format=%s | head -1")
      u/remove-trailing-newline))

(defn apply-stash--not-index [] ; TODO Change this to pop when are sure all is OK.
  (u/bash "git stash apply --quiet"))

(defn apply-stash-if-ends-with--not-index [s]
  (when (str/ends-with? (top-stash-name)
                        ;; TODO Note difference between these two things.
                        ;;      What's the best way to handle it?
                        s)
    ;; We created a stash; restore things.
    (apply-stash--not-index)))

(defn apply-stash [] ; TODO Change this to pop when are sure all is OK.
  (u/bash "git stash apply --quiet --index"))

(defn apply-stash-if-ends-with [s]
  (when (str/ends-with? (top-stash-name)
                        ;; TODO Note difference between these two things.
                        ;;      What's the best way to handle it?
                        s)
    ;; We created a stash; restore things.
    (apply-stash)))

(defn ref->commit-message [ref]
  (-> (u/bash "git show -s --format=%s" ref)
      u/remove-trailing-newline))

(defn top-commit-message [n]
  (-> (u/bash "git log --format=%s -n " n " | tail -1")
      u/remove-trailing-newline))

(defn current-commit-sha []
  (-> (u/bash "git rev-parse HEAD")
      u/remove-trailing-newline))

(defn safekeeping-stash-name [kind type commit-sha]
  (gstring/format "%s--%s--for-%s--%s"
                  kind
                  (u/make-timestamp)
                  commit-sha
                  type))

(defn ->sha [ref]
  (-> (u/bash "git rev-parse" ref)
      u/remove-trailing-newline))

(defn range->shas [ref-1 ref-2]
  (-> (u/bash "git rev-list --reverse" (str ref-1 ".." ref-2))
      u/split-on-newline))

(defn unpushed-commit-shas [remote-name]
  ;; TODO Add validation.
  (range->shas (str remote-name "/" (branch-name))
               "HEAD"))

(defn unpushed-commit-names [remote-name] ; TODO Should use `git rev-list`.
  ;; TODO Add validation.
  (-> (u/bash "git log --reverse --format=%s"
              (str remote-name "/" (branch-name) "..HEAD"))
      u/split-on-newline))

(defn changed-files--one-per-line [ref-1 ref-2]
  (u/bash "git diff-tree --no-commit-id --name-only -r" ref-1 ref-2))

(defn changed-files [ref-1 ref-2]
  (-> (changed-files--one-per-line ref-1 ref-2)
      u/split-on-newline))

(defn changed-files--single-line [ref-1 ref-2]
  (-> (changed-files--one-per-line ref-1 ref-2)
      u/remove-trailing-newline
      (str/replace "\n" " ")))

(defn add [path]
  (u/bash "git add" path))

(defn commit--quiet--no-verify--allow-empty [message]
  (u/bash "git commit --quiet --no-verify --allow-empty -m"
          message))

(defn commit--quiet--no-verify--allow-empty-v2 [& args]
  (apply u/bash "git commit --quiet --no-verify --allow-empty"
         args))

(defn push [& args]
  (apply u/bash "git push" args))

;;;; ___________________________________________________________________________

(defn run-some-testy-stuff [remote-name-from-command-line
                            remote-location
                            push-info]
  (println "________________________________________")
  (println (gstring/format "remote-name-from-command-line = \"%s\""
                           remote-name-from-command-line))
  (println "(remote-names) =" (remote-names))
  (println (gstring/format "(remote-name)  = \"%s\""
                           (remote-name)))
  (println (gstring/format "remote-location = \"%s\"" remote-location))
  (println (gstring/format "push-info = %s" push-info))
  (println (gstring/format "(->sha \"HEAD\") = \"%s\"" (->sha "HEAD")))
  (println (gstring/format "(range->shas \"HEAD~3\" \"HEAD\") = %s" (range->shas "HEAD~3" "HEAD")))
  (println "(unpushed-commit-shas \"origin\") =" (unpushed-commit-shas "origin"))
  (println (gstring/format "(unpushed-commit-names \"origin\") = %s"
                           (unpushed-commit-names "origin")))
  (println (gstring/format "branch-name = \"%s\""
                           (branch-name)))
  (println (gstring/format "top-stash-name = \"%s\""
                           (top-stash-name)))
  (println (gstring/format "commit message #1 = \"%s\""
                           (top-commit-message 1)))
  (println (gstring/format "commit message #2 = \"%s\""
                           (top-commit-message 2)))
  (println (gstring/format "commit message #3 = \"%s\""
                           (top-commit-message 3)))
  (println (gstring/format "safekeeping-stash-name = \"%s\""
                           (safekeeping-stash-name "the-kind"
                                                   "the-type"
                                                   "the-commit-sha")))
  (println (gstring/format "(current-commit-sha) = \"%s\""
                           (current-commit-sha)))
  (println (gstring/format "(-> push-info first second) = \"%s\""
                           (-> push-info first second)))
  (println "________________________________________"))
