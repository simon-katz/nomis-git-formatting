(ns nomis-git-stuff.cljfmt-with-local-formatting--push-wrapper.push-wrapper
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [nomis-git-stuff.common.git :as git]
            [nomis-git-stuff.common.utils :as u]
            [planck.core :as core]
            [planck.io :as io]
            [planck.shell :as shell]))

(defn stash [stash-name]
  (println "Stashing if dirty")
  (git/stash-if-dirty-include-untracked stash-name))

(defn reset-to-remote-commit [ref]
  (println "    Resetting (hard) to remote commit")
  (git/reset-hard ref))

(defn reformat-and-commit-if-dirty [sha for-remote-commit?]
  (println "    Applying cljfmt formatting")
  (u/bash "lein cljfmt fix")
  (git/add ".")
  (if-not (git/dirty?)
    (println "    cljfmt formatting made no changes -- not committing")
    (do
      (println (if for-remote-commit?
                 "    Committing: apply-cljfmt-formatting"
                 "    Committing"))
      (if for-remote-commit?
        (git/commit--quiet--no-verify--allow-empty "apply-cljfmt-formatting")
        (git/commit--quiet--no-verify--allow-empty-v2 "-C" sha)))))

(defn checkout [sha]
  (println "    Checking out" sha)
  (git/checkout-pathspec=dot sha))

(defn push []
  (println "Pushing")
  (git/push "--no-verify"))

(defn maybe-create-local-formatting-commit [user-commit-sha]
  (when (git/dirty?)
    (println "    Committing: apply-local-formatting")
    (git/commit--quiet--no-verify--allow-empty "apply-local-formatting")))

(defn restore-uncommitted-changes [stash-name]
  (println "    Restoring any uncommitted changes")
  (git/apply-stash-if-ends-with--not-index stash-name))

(defn push-wrapper []
  (let [remote-name        (git/remote-name)
        branch-name        (git/branch-name)
        ;; TODO Are you are making assumptions about the name of the
        ;;      remote branch?
        remote-branch-name (str remote-name "/" branch-name)
        unpushed-shas      (git/range->shas remote-branch-name "HEAD")
        n-unpushed-shas    (count unpushed-shas)
        user-commit-sha    (last unpushed-shas)]
    (println "unpushed-shas   =" unpushed-shas)
    (println "user-commit-sha =" user-commit-sha)
    (when unpushed-shas
      (let [pushed-sha (git/->sha (str "HEAD~" n-unpushed-shas))]
        (println "pushed-sha =" pushed-sha)
        (let [stash-name (git/safekeeping-stash-name
                          "_nomis-cljfmt-with-local-formatting--push-wrapper"
                          "push-wrapper"
                          user-commit-sha)]
          (do
            (stash stash-name)
            (println "Processing remote commit"
                     remote-branch-name
                     (git/->sha remote-branch-name)
                     (git/ref->commit-message remote-branch-name))
            (reset-to-remote-commit remote-branch-name)
            (reformat-and-commit-if-dirty pushed-sha true)
            (doseq [sha unpushed-shas]
              (println "Processing" sha (git/ref->commit-message sha))
              (checkout sha)
              (reformat-and-commit-if-dirty sha false))
            (push)
            (println "Doing post-push processing")
            (checkout user-commit-sha)
            (maybe-create-local-formatting-commit user-commit-sha)
            (restore-uncommitted-changes stash-name)))))))

;;;; TODO What happens when we need to force-push?
;;;;      The sequence of commits we get will be empty.
;;;; TODO What about command-line args?
;;;;      - Grrr! Would be so much better if you could use pre and post hooks.
;;;;      - Ah, here's an idea:
;;;         - But I don't think it worls, because there will be error exits
;;;;        - Do `git push`
;;;;          - So you have all the args that `git push` has
;;;;        - The pre-push hooks calls this script and passes in the parameters.
;;;;          - This script either
;;;;            - does no-hooks when calling git push
;;;;            - creates a special file fir the pre-push hook to see
;;;;        - This script exits
;;;;          - Maybe an error exit
;;;;            - but with a message to the user saying all OK
;;;;          - Maybe a 0 exit
;;;;            - But then it will try to push again I guess
;;;;              - What will be the result of that? An error?
;;;;          - And an error exit will confuse callers, so not good
;;;;        - Another idea:
;;;;          - In this script
;;;;            - Create a special file
;;;;            - Call `git push` with all of this scripts args
;;;;          - In pre-push hook
;;;;            - Check for the special file
;;;;            - Get the parameters and store them in a special file
;;;;            - exit 1 so that the push doesn't happen
;;;;          - In this script
;;;;            - Grab the stored parameters
;;;;            - So now you have the list of commits you need
;;;;            - After rewriting commits, call git push again
;;;;          - Q.
;;;;            - Think about all the possible args to git push.
;;;;              - You're not going to understand everything.
;;;;              - Maybe only allow a limited set of args
