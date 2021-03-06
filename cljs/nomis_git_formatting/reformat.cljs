(ns nomis-git-formatting.reformat
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [nomis-git-formatting.git :as git]
            [nomis-git-formatting.utils :as u]
            [planck.core :as core]
            [planck.io :as io]
            [planck.shell :as shell]))

(def doing-get-push-parameters-filename
  ".git/_nomis-git-formatting--doing-get-push-parameters")

(def remote-name-filename     ".git/_nomis-git-formatting--remote-name")
(def remote-location-filename ".git/_nomis-git-formatting--remote-location")
(def pre-push-stdin-filename  ".git/_nomis-git-formatting--pre-push-stdin")

(def doing-wrapped-push-filename ".git/_nomis-git-formatting--doing-wrapped-push")

(defn git-pre-push-stdin->push-info [s]
  (map u/split-on-space
       (u/split-on-newline s)))

(defn get-push-parameters [command-line-args]
  ;; TODO We might not be running in the root directory of the repo.
  ;;      Will need to do `(-> (u/bash "pwd") u/remove-trailing-newline)` and
  ;;      search upwards for .git. (Or is there a Planck way to get working
  ;;      directory?)
  (u/touch doing-get-push-parameters-filename)
  (try (let [;; TODO Think about problems with passing command line parameters around.
             res (apply shell/sh "git" "push" command-line-args)]
         (assert (not (zero? (:exit res))))
         (print (:out res))
         ;; Don't print the error output -- it will have an error because the
         ;; pre-push hook exited with an error.
         )
       (finally
         (io/delete-file doing-get-push-parameters-filename)))
  (let [remote-name     (-> (core/slurp remote-name-filename)
                            u/remove-trailing-newline)
        remote-location (-> (core/slurp remote-location-filename)
                            u/remove-trailing-newline)
        pre-push-stdin  (-> (core/slurp pre-push-stdin-filename)
                            u/remove-trailing-newline)
        push-info       (git-pre-push-stdin->push-info pre-push-stdin)]
    [remote-name
     remote-location
     push-info]))

(defn ensure-n-things-being-pushed-ok [push-info]
  (let [n-things-being-pushed (count push-info)]
    (when (> n-things-being-pushed 1)
      (u/exit-with-error
       (gstring/format
        "Don't know what to do unless a single thing is being pushed. We have %s: %s"
        n-things-being-pushed
        push-info)))))

(defn get-push-details [command-line-args]
  (let [[remote-name
         remote-location
         push-info] (get-push-parameters command-line-args)]
    (println "[wrapper] remote-name ="     remote-name)
    (println "[wrapper] remote-location =" remote-location)
    (println "[wrapper] push-info ="       push-info)
    (ensure-n-things-being-pushed-ok push-info)
    (let [single-push-item? (= 1 (count push-info))
          local-sha         (when single-push-item? (-> push-info first second))
          remote-sha        (when single-push-item? (-> push-info first (nth 3)))]
      (println "[wrapper] local-sha ="  local-sha)
      (println "[wrapper] remote-sha =" remote-sha)
      (println "[wrapper] HEAD ="       (git/->sha "HEAD"))
      [remote-name
       local-sha
       remote-sha])))

(defn stash [stash-name]
  (println "Stashing if dirty")
  (git/stash-if-dirty-include-untracked stash-name))

(defn print-rescue-instructions [push?
                                 remote-sha
                                 local-sha
                                 stash-name]
  (println "================================")
  (println "IF ANYTHING GOES WRONG, YOU CAN RECOVER LOCAL STATE WITH:")
  (when push?
    (println "    git reset --hard" remote-sha)
    (println "    git push --no-verify --force-with-lease"))
  (println "    git reset --hard" local-sha)
  (println "    git clean -qfd")
  (when (git/top-stash-ends-with? stash-name)
    (println "    git stash apply --index"))
  (println "================================"))

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

(defn checkout [sha print-sha?]
  (if print-sha?
    (println "    Checking out" sha)
    (println "    Checking out"))
  (git/checkout-pathspec=dot sha))

(defn push [command-line-args]
  (u/touch doing-wrapped-push-filename)
  (try (let [;; TODO Think about problems with passing command line parameters around.
             res (apply shell/sh "git" "push" command-line-args)]
         (print (:out res))
         (u/err-println (:err res)))
       (finally
         (io/delete-file doing-wrapped-push-filename))))

(defn maybe-create-local-formatting-commit []
  (when (git/dirty?)
    (let [msg "_nomis-restore-local-formatting"]
     (println "    Committing:" msg)
     (git/commit--quiet--no-verify--allow-empty msg))))

(defn restore-uncommitted-changes [stash-name]
  (println "    Restoring any uncommitted changes")
  (git/apply-stash-if-ends-with stash-name))

(defn reformat [{:keys [push?
                        type-for-stash]}]
  (let [push-default (git/push-default)]
   (when-not (= push-default "simple")
     (u/exit-with-error (str "Git push.default is "
                             push-default
                             "\nNot sure what to do when Git push.default is not simple.
Please teach the maintainer more about Git!"))))
  (let [command-line-args cljs.core/*command-line-args*
        _                 (do
                            (println (str "command-line-args = '"
                                          command-line-args
                                          "'"))
                            (println "(type command-line-args) ="
                                     (type command-line-args))
                            (println "Getting push parameters"))
        [remote-name
         local-sha
         remote-sha]      (get-push-details command-line-args)]
    (println "remote-name =" remote-name)
    (println "local-sha =" local-sha)
    (println "remote-sha =" remote-sha)
    (if (or (nil? local-sha)
            (nil? remote-sha))
      (do
        (println "Nothing to do -- exiting"))
      (let [branch-name     (git/branch-name)
            unpushed-shas   (git/range->shas remote-sha local-sha)
            n-unpushed-shas (count unpushed-shas)]
        (println "unpushed-shas   =" unpushed-shas)
        (when unpushed-shas
          (let [stash-name (git/safekeeping-stash-name
                            "_nomis-cljfmt-with-local-formatting--push-wrapper"
                            type-for-stash
                            local-sha)]
            (do
              (println "Stashing")
              (stash stash-name)
              (print-rescue-instructions push? remote-sha local-sha stash-name)
              (println "Processing remote commit"
                       remote-sha
                       (git/ref->commit-message remote-sha))
              (reset-to-remote-commit remote-sha)
              (reformat-and-commit-if-dirty remote-sha true)
              (doseq [sha unpushed-shas]
                (println "Processing" sha (git/ref->commit-message sha))
                (checkout sha false)
                (reformat-and-commit-if-dirty sha false))
              (when push?
                (println "Pushing")
                (push command-line-args))
              (println "Restoring uncommited state")
              (checkout local-sha true)
              (maybe-create-local-formatting-commit)
              (restore-uncommitted-changes stash-name))))))))

(defn reformat-and-push []
  (reformat {:push? true
             :type-for-stash "reformat-and-push"}))

(defn reformat-local []
  (reformat {:push? false
             :type-for-stash "reformat-local"}))
